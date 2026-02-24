# AutoBlade

## Stateful vs. Stateless DI

A stateful application stores client data across interactions, while a stateless application follows a request-response model.

For standard web APIs, a stateless application makes more sense. But there are contexts where we need state: real-time applications and orchestrations. Such apps can include live sessions, games, and interactive client-oriented workflows.

However, even for web APIs, you can take a new hybrid approach with the best of both worlds. In particular, a stateful architecture is far more performant and reliable to manage atomic transactions and business logic. You could create a basic state machine for your app, and have your microservices import and utilize that state machine. This allows for a shared architecture across all your microservices.

## What is AutoBlade?

An extension to Google Dagger with useful compile-time annotations for various software patterns. It focuses on stateful DI, unlike other libraries that utilize stateless DI.

## Development Stages

AutoBlade is intended to be a full framework consisting of the following content:
* Blade Core - the core dependency injection library extending Dagger with useful automation of module generation and standard software patterns (such as factory, builder, and strategy)
* Blade State - a state management library that follows the repository software pattern, offers validation systems, state snapshots, observativity, reactivity, event handling, and in-memory orchestration options. It also offers concurrent safe options.
* Blade Persist - a persistent state management library that extends Blade State by giving automatic serialization and saving of your state into an external source, and automatically hydrating your state from your data back from that source
* Blade Govern - a policy library that gives your application a meaningful way to describe business rules, disable/enable features, and pass custom configuration data to your services based on user settings and pre-defined rule sets.
* Blade Connect - a library that provides tooling to optimize your Blade builds/JARs as well as integration with your cloud project and/or APIs/microservices.

At the moment, Blade Core has completed development and is in the process of refactoring to minimize bugs and maximize code quality. The repository feature is partially developed for Blade State. The remaining items are in planning or unstarted.

Also in the process of ensuring Kotlin compatibility for Blade Core.
