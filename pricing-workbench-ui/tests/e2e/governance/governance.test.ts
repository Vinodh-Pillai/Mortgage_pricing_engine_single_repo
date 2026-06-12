// Governance, Compliance, and Partner E2E Tests
import { test, expect, describe, beforeAll, afterAll, beforeEach, afterEach } from "@playwright/test";
import { apiHelper, type ApiResponse, type TestContext } from "../core/helpers/api-helper";
import { uiHelper, type NavigationResult } from "../core/helpers/ui-helper";
import { driftDetector, type DriftFinding, type DriftReport, type BaselineExpectation } from "../core/drift/drift-detector";
import { personas, type PersonaRole, getPersona, getTestScenarios, getAccessibleRoutes, getRestrictedRoutes } from "../core/personas/personas";