package org.elder.sourcerer2.dbstore

sealed class DbstoreUnexpectedVersionException : Exception()

class NotFoundWhenExpectedException() : DbstoreUnexpectedVersionException()

class FoundWhenNotExpectedException(
        val currentVersion: DbstoreStreamVersion?
) : DbstoreUnexpectedVersionException()

class FoundWithDifferentVersionException(
        val currentVersion: DbstoreStreamVersion
) : DbstoreUnexpectedVersionException()
