package com.hackinglab.toki.data

/** UI hint only: PocketBase independently checks current authorization on DELETE. */
object DeletePermission {
    fun allowed(userId: String, owner: String, group: String, adminGroups: Set<String>): Boolean =
        userId.isNotBlank() && if (group.isBlank()) owner == userId else group in adminGroups
}
