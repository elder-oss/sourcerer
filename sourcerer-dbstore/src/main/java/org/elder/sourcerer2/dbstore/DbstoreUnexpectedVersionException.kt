package org.elder.sourcerer2.dbstore

import java.lang.Exception

sealed class DbstoreUnexpectedVersionException : Exception()

class NotFoundWhenExpectedException(): DbstoreUnexpectedVersionException()

class FoundWhenNotExpectedException(
        private val currentVersion: DbstoreStreamVersion?
): DbstoreUnexpectedVersionException()

class FoundWithDifferentVersionException(
        private val currentVersion: DbstoreStreamVersion
) : DbstoreUnexpectedVersionException()
