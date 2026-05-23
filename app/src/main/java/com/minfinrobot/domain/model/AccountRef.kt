package com.minfinrobot.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AccountRef(
    val id: String,
    val name: String,
    val type: String
)
