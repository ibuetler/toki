package com.hackinglab.toki

import com.hackinglab.toki.data.DeletePermission
import org.junit.Assert.*
import org.junit.Test

class DeletePermissionTest {
    @Test fun ownPrivateOnly() {
        assertTrue(DeletePermission.allowed("me", "me", "", emptySet()))
        assertFalse(DeletePermission.allowed("me", "other", "", emptySet()))
        assertFalse(DeletePermission.allowed("", "", "", emptySet()))
    }
    @Test fun groupRequiresAdministrationEvenForCreator() {
        assertFalse(DeletePermission.allowed("me", "me", "family", emptySet()))
        assertTrue(DeletePermission.allowed("me", "other", "family", setOf("family")))
        assertFalse(DeletePermission.allowed("me", "other", "work", setOf("family")))
    }
}
