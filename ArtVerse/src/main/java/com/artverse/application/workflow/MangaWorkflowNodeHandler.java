package com.artverse.application.workflow;

import java.util.Map;

public interface MangaWorkflowNodeHandler {

    MangaWorkflowRoute route();

    Map<String, Object> run(MangaWorkflowExecutionContext context);

    Map<String, Object> stream(MangaWorkflowExecutionContext context, MangaWorkflowStreamContext streamContext);
}
