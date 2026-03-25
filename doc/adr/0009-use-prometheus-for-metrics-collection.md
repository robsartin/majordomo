# 9. Use Prometheus for metrics collection

Date: 2026-03-24

## Status

Accepted

## Context

Each Majordomo service produces operational metrics (request counts, latencies, error rates, JVM stats). We need a metrics collection system that integrates naturally with Spring Boot and feeds data to Grafana (see ADR-0008) for visualization and alerting.

## Decision

We will use Prometheus as the metrics collection and storage system for all Majordomo services.

- Each Spring Boot service exposes a `/actuator/prometheus` endpoint via Micrometer and the Prometheus registry.
- Prometheus scrapes these endpoints on a regular interval.
- Custom application metrics (e.g., assets tracked, maintenance events scheduled) are registered through Micrometer's API, keeping application code decoupled from Prometheus specifics.
- Prometheus is the primary data source for Grafana dashboards and alerts.

## Consequences

- Pull-based scraping is simple to configure and does not require services to know about the metrics backend.
- Micrometer provides a vendor-neutral metrics API — switching from Prometheus to another backend (e.g., OpenTelemetry Collector) requires only a registry change.
- Spring Boot Actuator + Micrometer provides JVM, HTTP, and database metrics out of the box with minimal configuration.
- Prometheus must be deployed and configured with scrape targets for each service; service discovery or static config is needed as services scale.
