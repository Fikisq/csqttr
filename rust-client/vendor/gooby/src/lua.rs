use anyhow::{anyhow, bail, Context, Result};
use mlua::{
    ChunkMode, Function, HookTriggers, Lua, LuaOptions, StdLib, String as LuaString, Table, Value,
    VmState,
};
use std::{
    sync::atomic::{AtomicUsize, Ordering},
    time::{Duration, Instant},
};

use crate::tls::fragment_tls_sni_record;

const MAX_OUTPUT_BYTES: usize = 1024 * 1024;
const MAX_OUTPUT_CHUNKS: usize = 4096;
const MAX_VM_BYTES: usize = 8 * 1024 * 1024;

fn reset_budget(lua: &Lua) {
    let ticks = AtomicUsize::new(0);
    let started = Instant::now();
    lua.set_hook(
        HookTriggers::new().every_nth_instruction(1000),
        move |_, _| {
            if ticks.fetch_add(1, Ordering::Relaxed) >= 1000
                || started.elapsed() > Duration::from_millis(100)
            {
                return Err(mlua::Error::RuntimeError(
                    "Gooby Lua execution budget exceeded".into(),
                ));
            }
            Ok(VmState::Continue)
        },
    );
}

#[derive(Debug, Clone)]
pub struct TransformPlan {
    pub chunks: Vec<Vec<u8>>,
    pub low_ttl_first: bool,
    pub delay_ms: u64,
}

impl TransformPlan {
    pub fn passthrough(data: &[u8]) -> Self {
        Self {
            chunks: vec![data.to_vec()],
            low_ttl_first: false,
            delay_ms: 0,
        }
    }
}

/// One Lua VM per custom-strategy TCP connection. This lets scripts keep state
/// in globals/upvalues between packets/chunks without sharing state across users.
pub struct LuaProcessor {
    lua: Lua,
    process: Function,
    host: String,
    port: u16,
    sequence: u64,
}

impl LuaProcessor {
    pub fn new(source: &str, script_name: &str, host: &str, port: u16) -> Result<Self> {
        let lua = Lua::new_with(
            StdLib::TABLE | StdLib::STRING | StdLib::MATH | StdLib::BIT | StdLib::JIT,
            LuaOptions::default(),
        )?;
        lua.set_memory_limit(MAX_VM_BYTES)?;
        // Instruction hooks cannot bound compiled LuaJIT traces. Disable JIT before
        // loading untrusted source and do not expose an API to re-enable it.
        lua.load("jit.off(); jit.flush()").exec()?;
        // No filesystem, processes, native modules, bytecode or protected calls
        // which could swallow a budget error. Built-in presets do not use this VM.
        for name in [
            "os",
            "io",
            "package",
            "require",
            "ffi",
            "debug",
            "jit",
            "coroutine",
            "dofile",
            "loadfile",
            "load",
            "loadstring",
            "pcall",
            "xpcall",
            "collectgarbage",
            "newproxy",
            "print",
        ] {
            lua.globals().set(name, Value::Nil)?;
        }
        // LuaJIT's native pattern matcher can backtrack without invoking the hook.
        // Keep literal search available through the ordinary string API.
        lua.load(
            r#"
            local find = string.find
            string.find = function(s, needle, init, plain)
                assert(plain == true, "Gooby Lua string.find requires plain=true")
                -- LuaJIT uses a byte-by-byte search with a comparison at each match.
                assert(#s * #needle <= 4 * 1024 * 1024, "Gooby Lua literal search exceeds work limit")
                return find(s, needle, init, true)
            end
            string.match = nil
            string.gmatch = nil
            string.gfind = nil
            string.gsub = nil
            string.dump = nil
            "#,
        )
        .exec()?;
        install_gooby_api(&lua)?;
        reset_budget(&lua);

        lua.load(source)
            .set_name(script_name)
            .set_mode(ChunkMode::Text)
            .exec()
            .with_context(|| format!("executing {script_name}"))?;

        let process: Function = lua
            .globals()
            .raw_get("process")
            .with_context(|| format!("{script_name} must define function process(packet, ctx)"))?;

        Ok(Self {
            lua,
            process,
            host: host.to_owned(),
            port,
            sequence: 0,
        })
    }

    pub fn process(&mut self, packet: &[u8], first: bool) -> Result<TransformPlan> {
        reset_budget(&self.lua);
        let ctx = self.lua.create_table()?;
        ctx.set("host", self.host.as_str())?;
        ctx.set("port", self.port)?;
        ctx.set("direction", "client_to_server")?;
        ctx.set("first", first)?;
        ctx.set("sequence", self.sequence)?;

        let packet_value = self.lua.create_string(packet)?;
        let value: Value = self.process.call((packet_value, ctx))?;
        self.sequence = self.sequence.saturating_add(1);
        parse_return(value, packet)
    }
}

fn install_gooby_api(lua: &Lua) -> Result<()> {
    let api = lua.create_table()?;
    let fragment =
        lua.create_function(|lua, (packet, chunk_size): (LuaString, Option<usize>)| {
            let bytes = packet.as_bytes();
            let Some(chunks) = fragment_tls_sni_record(bytes.as_ref(), chunk_size.unwrap_or(2))
            else {
                return Ok(Value::Nil);
            };
            let table = lua.create_table()?;
            for (index, chunk) in chunks.into_iter().enumerate() {
                table.set(index + 1, lua.create_string(&chunk)?)?;
            }
            Ok(Value::Table(table))
        })?;
    api.set("fragment_tls_sni", fragment)?;
    lua.globals().set("gooby", api)?;
    Ok(())
}

fn parse_return(value: Value, original: &[u8]) -> Result<TransformPlan> {
    match value {
        Value::Nil => Ok(TransformPlan::passthrough(original)),
        Value::String(s) if s.as_bytes().len() <= MAX_OUTPUT_BYTES => Ok(TransformPlan {
            chunks: vec![s.as_bytes().to_vec()],
            low_ttl_first: false,
            delay_ms: 0,
        }),
        Value::Table(table) => parse_table(table, original),
        other => bail!(
            "process() must return nil, a binary string, or a table; got {}",
            other.type_name()
        ),
    }
}

#[cfg(test)]
mod sandbox_tests {
    use super::*;

    #[test]
    fn packet_api_and_per_connection_state_survive() {
        let mut processor = LuaProcessor::new(
            "n=0; function process(packet,ctx) n=n+1; assert(ctx.host=='test'); return {chunks={packet, tostring(n)}, delay_ms=1} end",
            "test", "test", 443).unwrap();
        assert_eq!(
            processor.process(b"abc", true).unwrap().chunks,
            vec![b"abc".to_vec(), b"1".to_vec()]
        );
        assert_eq!(processor.process(b"def", false).unwrap().chunks[1], b"2");
    }

    #[test]
    fn dangerous_libraries_are_unavailable() {
        LuaProcessor::new("assert(os==nil and io==nil and package==nil and require==nil and ffi==nil and debug==nil and jit==nil and coroutine==nil and dofile==nil and loadfile==nil and load==nil and loadstring==nil and pcall==nil and xpcall==nil and newproxy==nil); function process(p,c) return p end", "test", "test", 443).unwrap();
        assert!(LuaProcessor::new("os.execute('echo unsafe')", "test", "test", 443).is_err());
    }

    #[test]
    fn literal_search_survives_without_native_pattern_matching() {
        LuaProcessor::new(
            "assert(string.match == nil and string.gmatch == nil and string.gsub == nil); assert(('a.b'):find('.', 1, true) == 2); function process(p,c) return p end",
            "test", "test", 443,
        ).unwrap();
        assert!(LuaProcessor::new("string.find('aaa', 'a*')", "test", "test", 443).is_err());
        assert!(LuaProcessor::new(
            "string.find(string.rep('a', 8192), string.rep('a', 1024)..'b', 1, true)",
            "test",
            "test",
            443,
        )
        .is_err());
    }

    #[test]
    fn initialization_and_packet_loops_are_bounded() {
        assert!(LuaProcessor::new("while true do end", "test", "test", 443).is_err());
        let mut processor = LuaProcessor::new(
            "function process(p,c) while true do end end",
            "test",
            "test",
            443,
        )
        .unwrap();
        assert!(processor.process(b"test", true).is_err());
    }

    #[test]
    fn allocation_and_output_are_bounded() {
        let mut processor = LuaProcessor::new(
            "function process(p,c) return string.rep('x', 16777216) end",
            "test",
            "test",
            443,
        )
        .unwrap();
        assert!(processor.process(b"test", true).is_err());
        let mut processor = LuaProcessor::new(
            "function process(p,c) return string.rep('x', 1048577) end",
            "test",
            "test",
            443,
        )
        .unwrap();
        assert!(processor.process(b"test", true).is_err());
    }
}

fn parse_table(table: Table, original: &[u8]) -> Result<TransformPlan> {
    if table.get::<Option<bool>>("drop")?.unwrap_or(false) {
        return Ok(TransformPlan {
            chunks: Vec::new(),
            low_ttl_first: false,
            delay_ms: 0,
        });
    }

    let low_ttl_first = table.get::<Option<bool>>("low_ttl_first")?.unwrap_or(false);
    let delay_ms = table
        .get::<Option<u64>>("delay_ms")?
        .unwrap_or(0)
        .min(5_000);

    let chunks_table = table.get::<Option<Table>>("chunks")?;
    let has_explicit_chunks = chunks_table.is_some();
    let source_table = chunks_table.unwrap_or_else(|| table.clone());
    let mut chunks = Vec::new();
    let mut total = 0usize;

    for value in source_table.sequence_values::<Value>() {
        if chunks.len() >= MAX_OUTPUT_CHUNKS {
            bail!("Lua plan exceeds {MAX_OUTPUT_CHUNKS} chunks");
        }
        let value = value?;
        let Value::String(s) = value else {
            bail!("every Lua chunk must be a binary string");
        };
        let bytes = s.as_bytes();
        total = total
            .checked_add(bytes.len())
            .ok_or_else(|| anyhow!("Lua output length overflow"))?;
        if total > MAX_OUTPUT_BYTES {
            bail!("Lua plan exceeds {MAX_OUTPUT_BYTES} output bytes");
        }
        chunks.push(bytes.to_vec());
    }

    if chunks.is_empty() {
        if has_explicit_chunks {
            return Ok(TransformPlan {
                chunks,
                low_ttl_first,
                delay_ms,
            });
        }
        return Ok(TransformPlan::passthrough(original));
    }

    Ok(TransformPlan {
        chunks,
        low_ttl_first,
        delay_ms,
    })
}
