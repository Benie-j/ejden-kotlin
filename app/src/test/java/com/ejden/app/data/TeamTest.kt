package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamTest {

    @Test
    fun proprietaireParDefaut() {
        val team = TeamRepository(InMemoryStore())
        assertEquals(TEAM_OWNER_ID, team.getActiveId())
        assertEquals(TeamRole.Admin, team.getActiveRole())
        for (permission in TeamPermission.entries) assertTrue(team.can(permission))
    }

    @Test
    fun ajoutEtRetraitDUnUtilisateur() {
        val team = TeamRepository(InMemoryStore())
        assertEquals(AddMemberResult.EmptyName, team.addMember("   ", TeamRole.Seller))
        assertEquals(AddMemberResult.Ok, team.addMember("  Awa   Diop ", TeamRole.Seller))
        assertEquals(AddMemberResult.Duplicate, team.addMember("awa diop", TeamRole.Manager))

        val member = team.getMembers().single()
        assertEquals("Awa Diop", member.name)
        assertEquals(TeamRole.Seller, member.role)

        assertTrue(team.setActive(member.id))
        assertEquals(member.id, team.getActiveId())
        assertTrue(team.removeMember(member.id))
        assertEquals(TEAM_OWNER_ID, team.getActiveId())
        assertFalse(team.removeMember(member.id))
    }

    @Test
    fun droitsSelonLeRole() {
        val team = TeamRepository(InMemoryStore())
        team.addMember("Awa", TeamRole.Seller)
        team.setActive(team.getMembers().single().id)

        assertTrue(team.can(TeamPermission.Sell))
        assertTrue(team.can(TeamPermission.ViewProducts))
        assertFalse(team.can(TeamPermission.ManageProducts))
        assertFalse(team.can(TeamPermission.Finances))
        assertFalse(team.can(TeamPermission.Settings))
    }

    @Test
    fun permissionsModifiables() {
        val team = TeamRepository(InMemoryStore())
        assertFalse(team.permissionsOf(TeamRole.Seller).contains(TeamPermission.Finances))
        assertTrue(team.setPermission(TeamRole.Seller, TeamPermission.Finances, true))
        assertTrue(team.permissionsOf(TeamRole.Seller).contains(TeamPermission.Finances))
        assertTrue(team.setPermission(TeamRole.Seller, TeamPermission.Sell, false))
        assertFalse(team.permissionsOf(TeamRole.Seller).contains(TeamPermission.Sell))

        assertFalse(team.setPermission(TeamRole.Admin, TeamPermission.Sell, false))
        assertFalse(team.setPermission(TeamRole.Seller, TeamPermission.Settings, true))
        assertEquals(TeamPermission.entries.toSet(), team.permissionsOf(TeamRole.Admin))
    }
}
