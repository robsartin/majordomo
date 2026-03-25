# 8. Use Grafana for observability dashboards

Date: 2026-03-24

## Status

Accepted

## Context

With multiple independent services, we need centralized visibility into system health, performance, and behavior. Dashboards should surface metrics from all services in one place and support alerting when things go wrong.

## Decision

We will use Grafana as the dashboard and visualization layer for observability across all Majordomo services.

- Grafana will consume metrics from Prometheus (see ADR-0009) as its primary data source.
- Each service will have a standard dashboard covering key indicators: request rate, error rate, latency, and resource utilization.
- Dashboards will be defined as code (JSON/YAML provisioning) and version-controlled alongside the services they describe.
- Grafana alerting will be used for operational notifications.

## Consequences

- Single pane of glass for the health of all services.
- Dashboards-as-code ensures they are reproducible, reviewable, and versioned.
- Grafana is widely adopted and has strong community support for Spring Boot and Prometheus integrations.
- Adds an infrastructure dependency that must be deployed and maintained alongside the services.
