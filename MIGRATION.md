Stream version on an eventdata object now always relates to the stream it was written _to_,
and repository version always relates to the version within the repository
that it was read from, regardless if read directly or used as a subscription.

Reading events and subscription for events now always use an exclusive version, e.g.
the version provided as the position to read or subscribe from will never be returned.
Previously, using version 0 when reading events would read from and including 0, but subscribing
would subscribe to events following 0.
