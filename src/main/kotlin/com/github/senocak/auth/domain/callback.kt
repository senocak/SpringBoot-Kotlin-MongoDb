package com.github.senocak.auth.domain

import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveCallback
import org.springframework.stereotype.Component

@Component
class DefaultingEntityCallback: BeforeSaveCallback<User>, BeforeConvertCallback<User> {
    override fun onBeforeSave(entity: User, document: org.bson.Document, collection: String): User = entity
        .also { println("---------------------onBeforeSave---------------------") }

    override fun onBeforeConvert(entity: User, collection: String): User = entity
        .also { println("---------------------onBeforeConvert---------------------") }
}