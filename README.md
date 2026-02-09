# AutoBlade

## Stateful vs. Stateless DI

A stateful application stores client data across interactions, while a stateless application follows a request-response model.

For standard web APIs, a stateless application makes more sense. But there are contexts where we need state: real-time applications and orchestrations. Such apps can include live sessions, games, and interactive client-oriented workflows.

However, even for web APIs, you can take a new hybrid approach with the best of both worlds. In particular, a stateful architecture is far more performant and reliable to manage atomic transactions and business logic. You could create a basic state machine for your app, and have your microservices import and utilize that state machine. This allows for a shared architecture across all your microservices.

## What is AutoBlade?

An extension to Google Dagger with useful compile-time annotations for various software patterns. It focuses on stateful DI, unlike other libraries that utilize stateless DI.

**Project is currently in design phase and will start development soon**
