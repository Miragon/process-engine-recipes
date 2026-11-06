// Generated from docs/api/taskmanager.json by `npm run generate:api` (openapi-typescript). Do not edit.

export interface paths {
    "/api/applications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["applications"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["subscribe"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/forms/{domain}/{formRef}/frame": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["frame"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/start-forms": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["startForms"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/start-forms/{domain}/{id}/form": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["startForm"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/start-forms/{domain}/{id}/submission": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["submit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tasks/facets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["facets"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tasks/{userTaskKey}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["update"];
        trace?: never;
    };
    "/api/tasks/{userTaskKey}/assignment": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["claim"];
        delete: operations["unclaim"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tasks/{userTaskKey}/completion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["complete"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tasks/{userTaskKey}/draft": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["draft"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tasks/{userTaskKey}/form": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["form"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tasks/{userTaskKey}/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["history"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["users"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        ApplicationDto: {
            domain: string;
            label: string;
        };
        DemoUserView: {
            groups: string[];
            id: string;
            name: string;
        };
        Facet: {
            /** Format: int64 */
            count: number;
            label: string;
            value: string;
        };
        Facets: {
            candidateGroups: components["schemas"]["Facet"][];
            domains: components["schemas"]["Facet"][];
            processes: components["schemas"]["Facet"][];
            states: components["schemas"]["Facet"][];
        };
        FormView: {
            data: {
                [key: string]: unknown;
            };
            draftable: boolean;
            formRef: string;
            readOnly: boolean;
            type: string;
        };
        HistoryEntry: {
            action?: string | null;
            assignee?: string | null;
            changedAttributes: string[];
            eventType: string;
            /** Format: date-time */
            occurredAt: string;
            source: string;
        };
        HtmlFormView: Omit<WithRequired<components["schemas"]["FormView"], "data" | "draftable" | "formRef" | "readOnly">, "type"> & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "html";
        };
        JsonFormsView: Omit<WithRequired<components["schemas"]["FormView"], "data" | "draftable" | "formRef" | "readOnly">, "type"> & {
            schema: {
                [key: string]: unknown;
            };
            uiSchema?: {
                [key: string]: unknown;
            } | null;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "json-forms";
        };
        SseEmitter: {
            /** Format: int64 */
            timeout?: number;
        };
        StartFormEntry: {
            description?: string | null;
            domain: string;
            domainLabel: string;
            formRef: string;
            id: string;
            label: string;
        };
        StartResult: {
            businessId?: string | null;
            processInstanceKey: string;
        };
        SubmissionBody: {
            data: {
                [key: string]: unknown;
            };
        };
        TaskDto: {
            actions: ("CLAIM" | "UNCLAIM" | "UPDATE" | "COMPLETE")[];
            assignee?: string | null;
            businessId?: string | null;
            candidateGroups: string[];
            candidateUsers: string[];
            /** Format: date-time */
            closedAt?: string | null;
            closedBy?: string | null;
            /** Format: date-time */
            createdAt: string;
            domain: string;
            domainLabel: string;
            /** Format: date-time */
            dueDate?: string | null;
            elementId: string;
            /** Format: date-time */
            followUpDate?: string | null;
            formRef?: string | null;
            name: string;
            /** Format: int32 */
            priority: number;
            processDefinitionId: string;
            /** Format: int32 */
            processDefinitionVersion: number;
            processInstanceKey: string;
            processName?: string | null;
            /** @enum {string} */
            state: "OPEN" | "COMPLETED" | "CANCELED";
            subject?: string | null;
            /** Format: date-time */
            updatedAt: string;
            userTaskKey: string;
        };
        TaskPage: {
            /** Format: int32 */
            from: number;
            items: components["schemas"]["TaskDto"][];
            /** Format: int32 */
            limit: number;
            /** Format: int64 */
            total: number;
        };
        TaskUpdate: {
            candidateGroups?: string[] | null;
            /** Format: date-time */
            dueDate?: string | null;
            /** Format: int32 */
            priority?: number | null;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    applications: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApplicationDto"][];
                };
            };
        };
    };
    subscribe: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/event-stream": components["schemas"]["SseEmitter"];
                };
            };
        };
    };
    frame: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                domain: string;
                formRef: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    me: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DemoUserView"];
                };
            };
        };
    };
    startForms: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StartFormEntry"][];
                };
            };
        };
    };
    startForm: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path: {
                domain: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HtmlFormView"] | components["schemas"]["JsonFormsView"];
                };
            };
        };
    };
    submit: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path: {
                domain: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SubmissionBody"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StartResult"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: {
                state?: "OPEN" | "COMPLETED" | "CANCELED" | "CLOSED" | "ALL";
                domain?: string;
                assignment?: "MINE" | "UNASSIGNED" | "ANY";
                candidateGroup?: string;
                processDefinitionId?: string;
                priorityMin?: number;
                dueBefore?: string;
                q?: string;
                sort?: "CREATED_AT" | "PRIORITY" | "DUE_DATE";
                descending?: boolean;
                from?: number;
                limit?: number;
            };
            header?: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskPage"];
                };
            };
        };
    };
    facets: {
        parameters: {
            query?: {
                state?: "OPEN" | "COMPLETED" | "CANCELED" | "CLOSED" | "ALL";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Facets"];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User"?: string;
            };
            path: {
                userTaskKey: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskDto"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path: {
                userTaskKey: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TaskUpdate"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskDto"];
                };
            };
        };
    };
    claim: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path: {
                userTaskKey: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskDto"];
                };
            };
        };
    };
    unclaim: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path: {
                userTaskKey: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskDto"];
                };
            };
        };
    };
    complete: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path: {
                userTaskKey: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SubmissionBody"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    draft: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path: {
                userTaskKey: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SubmissionBody"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    form: {
        parameters: {
            query?: never;
            header: {
                /** @description One of the demo users (GET /api/users), for example alice */
                "X-Demo-User": string;
            };
            path: {
                userTaskKey: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HtmlFormView"] | components["schemas"]["JsonFormsView"];
                };
            };
        };
    };
    history: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userTaskKey: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HistoryEntry"][];
                };
            };
        };
    };
    users: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DemoUserView"][];
                };
            };
        };
    };
}
type WithRequired<T, K extends keyof T> = T & {
    [P in K]-?: T[P];
};
