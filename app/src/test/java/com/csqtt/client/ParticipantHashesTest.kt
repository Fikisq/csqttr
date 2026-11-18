package com.csqtt.client

import org.junit.Assert.assertEquals
import org.junit.Test

class ParticipantHashesTest {
    private val a = "a".repeat(20)
    private val b = "b".repeat(20)
    private val c = "c".repeat(20)

    @Test
    fun selectedGroupIsTheOnlySource() {
        assertEquals("$a,$b", participantHashes(listOf(a, b), ManualHashGroup.Owner, c))
        assertEquals(c, participantHashes(listOf(a, b), ManualHashGroup.Own, c))
        assertEquals("", participantHashes(listOf(a, b), ManualHashGroup.Own, ""))
    }

    @Test
    fun defaultUsesOwnerOnlyWhenOwnerValuesExist() {
        assertEquals(ManualHashGroup.Owner, selectManualHashGroup(listOf(a), null))
        assertEquals(ManualHashGroup.Own, selectManualHashGroup(emptyList(), ManualHashGroup.Owner))
    }

    @Test
    fun explicitSelectionWinsAndFullyRemovedOwnerMigratesToOwn() {
        assertEquals(ManualHashGroup.Own, selectManualHashGroup(listOf(a, b), ManualHashGroup.Own))
        assertEquals(ManualHashGroup.Owner, selectManualHashGroup(listOf(a), ManualHashGroup.Owner, setOf(a)))
        assertEquals(ManualHashGroup.Own, selectManualHashGroup(listOf(a, b), null, setOf(a, b)))
        assertEquals(ManualHashGroup.Owner, selectManualHashGroup(listOf(a, b), null, setOf(a)))
    }

}
