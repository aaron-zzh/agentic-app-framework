package com.xuejiai.aaf.module.ai.aigc;

/** AIGC 自定义操作权限码及其方法安全表达式。 */
public final class AigcAuthorities {

    public static final String PROJECT_READ = "aigc:project:read";
    public static final String PROJECT_CREATE = "aigc:project:create";
    public static final String PROJECT_UPDATE = "aigc:project:update";
    public static final String PROJECT_ACTION = "aigc:project:action";
    public static final String OBJECT_VERSION_ADOPT = "aigc:object-version:adopt";
    public static final String PROJECT_REVIEW = "aigc:project:review";
    public static final String PROJECT_LIFECYCLE = "aigc:project:lifecycle";
    public static final String EXECUTION_RUN_READ = "aigc:execution-run:read";
    public static final String EXECUTION_RUN_EXECUTE = "aigc:execution-run:execute";
    public static final String WORK_READ = "aigc:work:read";
    public static final String WORK_COLLECT = "aigc:work:collect";
    public static final String WORK_PUBLISH = "aigc:work:publish";
    public static final String WORK_ARCHIVE = "aigc:work:archive";
    public static final String TIMELINE_READ = "aigc:timeline:read";
    public static final String TIMELINE_CREATE = "aigc:timeline:create";
    public static final String TIMELINE_UPDATE = "aigc:timeline:update";
    public static final String TIMELINE_DELETE = "aigc:timeline:delete";

    public static final String HAS_PROJECT_READ = "hasPermission(null, '" + PROJECT_READ + "')";
    public static final String HAS_PROJECT_CREATE = "hasPermission(null, '" + PROJECT_CREATE + "')";
    public static final String HAS_PROJECT_UPDATE = "hasPermission(null, '" + PROJECT_UPDATE + "')";
    public static final String HAS_PROJECT_ACTION = "hasPermission(null, '" + PROJECT_ACTION + "')";
    public static final String HAS_OBJECT_VERSION_ADOPT =
            "hasPermission(null, '" + OBJECT_VERSION_ADOPT + "')";
    public static final String HAS_PROJECT_REVIEW = "hasPermission(null, '" + PROJECT_REVIEW + "')";
    public static final String HAS_PROJECT_LIFECYCLE =
            "hasPermission(null, '" + PROJECT_LIFECYCLE + "')";
    public static final String HAS_EXECUTION_RUN_READ =
            "hasPermission(null, '" + EXECUTION_RUN_READ + "')";
    public static final String HAS_EXECUTION_RUN_EXECUTE =
            "hasPermission(null, '" + EXECUTION_RUN_EXECUTE + "')";
    public static final String HAS_WORK_READ = "hasPermission(null, '" + WORK_READ + "')";
    public static final String HAS_WORK_COLLECT = "hasPermission(null, '" + WORK_COLLECT + "')";
    public static final String HAS_WORK_PUBLISH = "hasPermission(null, '" + WORK_PUBLISH + "')";
    public static final String HAS_WORK_ARCHIVE = "hasPermission(null, '" + WORK_ARCHIVE + "')";
    public static final String HAS_TIMELINE_READ = "hasPermission(null, '" + TIMELINE_READ + "')";
    public static final String HAS_TIMELINE_CREATE = "hasPermission(null, '" + TIMELINE_CREATE + "')";
    public static final String HAS_TIMELINE_UPDATE = "hasPermission(null, '" + TIMELINE_UPDATE + "')";
    public static final String HAS_TIMELINE_DELETE = "hasPermission(null, '" + TIMELINE_DELETE + "')";

    private AigcAuthorities() {}
}
