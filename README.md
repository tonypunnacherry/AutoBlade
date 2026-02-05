# AutoBlade

## Stateful vs. Stateless DI

A stateful application stores client data across interactions, while a stateless application follows a request-response model.

For standard web APIs, a stateless application makes more sense. But there are contexts where we need state: real-time applications and orchestrations. Such apps can include live sessions, games, and interactive client-oriented workflows.

## What is AutoBlade?

An extension to Google Dagger with useful compile-time annotations for various software patterns. It focuses on stateful DI, unlike other libraries that utilize stateless DI. If you are building a traditional web API, this isn't the framework for you. 

**Project is currently in design phase and will start development soon**
