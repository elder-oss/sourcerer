### About Elder

Elder is a UK based introductory care agency that uses technology to rethink in-home care for
elderly, for more information, see [www.elder.org](http://www.elder.org/?utm_source=github).
For questions about Elder open source software or technology at Elder in general, please contact
tech at elder dot org.

Sourcerer
=========

## Overview

Sourcerer is an **opinionated**, **functional**, and **storage agnostic** framework for
implementing a CQRS architecture in Java 8 using event sourcing. 

### Sourcerer is opinionated

Sourcerer is a framework that supports a set of core concepts used in CQRS/ES architectures and
has its own opinion on what these building blocks are and how they are implemented. While it can
be used as an abstraction layer for any event based system, it is designed to enable the user to
express business logic in terms of specific patterns such as commands, operations, and subscriptions
(see below).

### Sourcerer is functional

Sourcerer embraces functional style programming - preferring to use immutable dumb objects over
object oriented stateful aggregates. State is expressed as a function over events, and events are
created explicitly, rather than as an implementation detail hidden inside an aggregate object.

### Sourcerer is storage agnostic

The storage backend used for the reference implementation and used in production at Elder is 
[EventStore](http://www.geteventstore.com), but alternative storage backends can be used by
implementing a [single interface](https://github.com/elder-oss/sourcerer/blob/master/sourcerer-core/src/main/java/org/elder/sourcerer/EventRepository.java).

## Patterns

While sourcerer uses a number of lower level abstractions to re-use implementation logic and allow
for extensibility, normal business logic will usually only need to deal with the patterns outlined
below.

### Commands

A sourcerer command is a function that, when executed, creates or updates an event sourced
aggregate. Commands are created from a
[Command Factory](https://github.com/elder-oss/sourcerer/blob/master/sourcerer-core/src/main/java/org/elder/sourcerer/CommandFactory.java), implicitly bound to an underlying event repository and projection. Commands are created from an operation (see below), as well as additional metadata such as whether it can create new aggregates, whether it requires changes to applied atomically (with no concurrent changes) etc.

#### Aggregate Projections

A command factory is bound to an
[Aggregate Projection](https://github.com/elder-oss/sourcerer/blob/master/sourcerer-core/src/main/java/org/elder/sourcerer/AggregateProjection.java)
that contains the logic required to re-build aggregate state from a sequence of events.
Projections can also be used directly in operations (see below) to preview state changes before
they have been committed to the underlying event repository.

#### Operations

An Operation, in sourcerer, is created from a function (not necessarily a pure function) that
provides the business logic for a command. Operations accept an optional current state of an
aggregate, an optional parameter payload, and returns a seqence of events describing the changes
to the aggregate. Operations may use external servics and data sources to generate new events, but
do not directly read or persist events related to the aggregate being updated - and as such can be
executed speculatively for "dry run" scenarios and unit tested without involving event persistence.
Operations are invoked by commands (see above) that deal with id, recreating state from events,
persisting events and providing optimistic concurrency if requested.

Operations are usually created from a method reference using the
[Operations](https://github.com/elder-oss/sourcerer/blob/master/sourcerer-core/src/main/java/org/elder/sourcerer/Operations.java)
class, rather than by implementing the
[Operation](https://github.com/elder-oss/sourcerer/blob/master/sourcerer-core/src/main/java/org/elder/sourcerer/Operation.java)
interface directly. The framework provides a large number of Operation Handler functional interfaces
matching common method type signatures and allows for automatic conversion from method references
in Java 8.

### Subscriptions

Sourcerer subscriptions are used to respond to updates to aggregates (via emitted events). In the
recommended sourcerer architecture, subscriptions is the only way in which materialized query models
are being created and kept up-to-date. Subscriptions can also be used to trigger side effects on
events, such as sending emails when certain actions are met.

Sourcerer subscriptions are responsible for keeping track of their own position in an event stream
- being either a stream of events with a particular stream id, or a stream of all events of a given
base type (e.g. all events related to a User). They must cope gracefully with being restarted at
any point in time, due to network disconnects, process restarts etc, so it is recommended that the
implementation is effectively idempotent - allowing events to be replayed without extra unwanted
side effects.

Subscriptions are created from an 
[EventSubscriptionFactory](https://github.com/elder-oss/sourcerer/blob/master/sourcerer-core/src/main/java/org/elder/sourcerer/EventSubscriptionFactory.java),
implicitly bound to an event repository and event type, by providing an implementation of
[EventSubscriptionHandler](https://github.com/elder-oss/sourcerer/blob/master/sourcerer-core/src/main/java/org/elder/sourcerer/EventSubscriptionHandler.java).
The default subscription factory implementation will create subscriptions that handle automatic
restarts on errors with exponential back-off delays, and efficient batching that dynamically
adjusts the number of events that the subscription handler is asked to process at a time depending
on the circumstances (live vs replay of old events, slow vs fast subscription handler).

## Sample Projects

For sample projects using Sourcerer to implement CQRS with event sourcing, see [sourcerer-samples](https://github.com/elder-oss/sourcerer-samples).

## Implementation specifics

### Serialization

The core sourcerer modules are agnostic to the format used to serialize and persist events, leaving
this concern to the storage specific event repository implementation. The reference implementation
using EventStore uses Jackson to serialize and deserialize Java objects. When using polymorphic
event types (where individual event types on a given stream are represented in a Java class
hierarchy), special concern must be taken to annotate the types appropriately, see
[Jackson Polymorphic Deserialization](http://wiki.fasterxml.com/JacksonPolymorphicDeserialization).

### Use of Kotlin

Sourcerer does not use or have dependencies on Kotlin, however, the sample projects and production
code that uses the framework does. Using Kotlin
[sealed classes](https://kotlinlang.org/docs/reference/classes.html#sealed-classes) to represent
events in combination with [when expressions](https://kotlinlang.org/docs/reference/control-flow.html#when-expression)
provides type safe runtime inspection of event types that would only be possible in plain Java
through runtime type inspections with unsafe downcasts, or using variations of the
[visitor pattern](https://en.wikipedia.org/wiki/Visitor_pattern).

### Spring support

Sourcerer provides optional support for use with Spring through standalone modules.

## Supported storage backends

The reference implementation of sourecerer event repository uses EventStore as the backing data
store, and the design of event repository abstractions have admittedly been influenced by the
primitives offered by this product. In fact, sourcerer comes with two implementations - one based
in the official [EventStore.JVM client](https://github.com/EventStore/EventStore.JVM),
and one based on the [Java 8 native client](https://github.com/msemys/esjc). The preference at Elder
is to use the esjc client, as this avoids dependencies on the Akka runtime, and has overall caused
less unexpected surprises. The EventStore.JVM storage backend is maintained for compatibility only
but not actively in use by Elder.

Other backends such as in memory event stores or ones backed by traditional databases plus a
messaging layer can be used by implementing
[EventRepository](https://github.com/elder-oss/sourcerer/blob/master/sourcerer-core/src/main/java/org/elder/sourcerer/EventRepository.java).
