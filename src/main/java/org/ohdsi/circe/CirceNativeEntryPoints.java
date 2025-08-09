package org.ohdsi.circe;

import org.ohdsi.circe.cohortdefinition.CohortExpression;
import org.ohdsi.circe.cohortdefinition.CohortExpressionQueryBuilder;
import org.ohdsi.circe.cohortdefinition.CohortExpressionQueryBuilder.BuildExpressionQueryOptions;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.io.PrintWriter;
import java.io.StringWriter;

// GraalVM native-image entrypoints
public class CirceNativeEntryPoints {
    // Simple holder for result to keep reference alive until copying by native layer.
    private static final ThreadLocal<String> LAST_RESULT = new ThreadLocal<>();

    // Expected C symbol: circe_cohort_json_to_sql
    @CEntryPoint(name = "circe_build_cohort_sql")
    public static CCharPointer buildCohortSql(
            @CEntryPoint.IsolateThreadContext IsolateThread thread,
            CCharPointer exprJson,
            CCharPointer optionsJson) {
        try {
            String expr = CTypeConversion.toJavaString(exprJson);
            String opts = CTypeConversion.toJavaString(optionsJson);
            BuildExpressionQueryOptions parsedOpts = null;
            if (opts != null && !opts.isEmpty()) {
                try { parsedOpts = BuildExpressionQueryOptions.fromJson(opts); } catch (Exception e) { parsedOpts = null; }
            }
            CohortExpression expression = CohortExpression.fromJson(expr);
            CohortExpressionQueryBuilder builder = new CohortExpressionQueryBuilder();
            String sql = builder.buildExpressionQuery(expression, parsedOpts);
            LAST_RESULT.set(sql);
            return CTypeConversion.toCString(sql).get();
        } catch (Throwable t) {
            // Capture full stack for debugging
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("/* circe error: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(pw);
            pw.println("*/");
            pw.flush();
            String msg = sw.toString();
            LAST_RESULT.set(msg);
            return CTypeConversion.toCString(msg).get();
        }
    }
}
