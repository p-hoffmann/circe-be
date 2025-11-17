package org.ohdsi.circe;

import org.ohdsi.circe.cohortdefinition.CohortExpression;
import org.ohdsi.circe.cohortdefinition.CohortExpressionQueryBuilder;
import org.ohdsi.circe.cohortdefinition.CohortExpressionQueryBuilder.BuildExpressionQueryOptions;
import org.ohdsi.sql.SqlRender;
import org.ohdsi.sql.SqlTranslate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ohdsi.circe.check.Checker;
import org.ohdsi.circe.check.Warning;

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

    // SQL Rendering and Translation functionality
    @CEntryPoint(name = "circe_sql_render")
    public static CCharPointer sqlRender(
            @CEntryPoint.IsolateThreadContext IsolateThread thread,
            CCharPointer sqlTemplate,
            CCharPointer parametersJson) {
        try {
            String template = CTypeConversion.toJavaString(sqlTemplate);
            String params = CTypeConversion.toJavaString(parametersJson);
            
            String renderedSql;
            if (params != null && !params.isEmpty()) {
                // Parse JSON parameters for rendering
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> paramMap = mapper.readValue(params, Map.class);
                    
                    String[] paramNames = paramMap.keySet().toArray(new String[0]);
                    String[] paramValues = new String[paramNames.length];
                    for (int i = 0; i < paramNames.length; i++) {
                        Object value = paramMap.get(paramNames[i]);
                        paramValues[i] = value != null ? value.toString() : "";
                    }
                    
                    renderedSql = SqlRender.renderSql(template, paramNames, paramValues);
                } catch (Exception e) {
                    // If JSON parsing fails, render without parameters
                    renderedSql = SqlRender.renderSql(template, new String[0], new String[0]);
                }
            } else {
                renderedSql = SqlRender.renderSql(template, new String[0], new String[0]);
            }
            
            LAST_RESULT.set(renderedSql);
            return CTypeConversion.toCString(renderedSql).get();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("/* sqlrender error: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(pw);
            pw.println("*/");
            pw.flush();
            String msg = sw.toString();
            LAST_RESULT.set(msg);
            return CTypeConversion.toCString(msg).get();
        }
    }

    @CEntryPoint(name = "circe_sql_translate")
    public static CCharPointer sqlTranslate(
            @CEntryPoint.IsolateThreadContext IsolateThread thread,
            CCharPointer sql,
            CCharPointer targetDialect) {
        try {
            String sqlString = CTypeConversion.toJavaString(sql);
            String dialect = CTypeConversion.toJavaString(targetDialect);
            
            // Default to SQL Server if no dialect specified
            if (dialect == null || dialect.isEmpty()) {
                dialect = "sql server";
            }
            
            String translatedSql = SqlTranslate.translateSql(sqlString, dialect);
            LAST_RESULT.set(translatedSql);
            return CTypeConversion.toCString(translatedSql).get();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("/* sqltranslate error: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(pw);
            pw.println("*/");
            pw.flush();
            String msg = sw.toString();
            LAST_RESULT.set(msg);
            return CTypeConversion.toCString(msg).get();
        }
    }

    @CEntryPoint(name = "circe_sql_render_translate")
    public static CCharPointer sqlRenderAndTranslate(
            @CEntryPoint.IsolateThreadContext IsolateThread thread,
            CCharPointer sqlTemplate,
            CCharPointer targetDialect,
            CCharPointer parametersJson) {
        try {
            String template = CTypeConversion.toJavaString(sqlTemplate);
            String dialect = CTypeConversion.toJavaString(targetDialect);
            String params = CTypeConversion.toJavaString(parametersJson);
            
            // First render the SQL template
            String renderedSql;
            if (params != null && !params.isEmpty()) {
                // Parse JSON parameters for rendering
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> paramMap = mapper.readValue(params, Map.class);
                    
                    String[] paramNames = paramMap.keySet().toArray(new String[0]);
                    String[] paramValues = new String[paramNames.length];
                    for (int i = 0; i < paramNames.length; i++) {
                        Object value = paramMap.get(paramNames[i]);
                        paramValues[i] = value != null ? value.toString() : "";
                    }
                    
                    renderedSql = SqlRender.renderSql(template, paramNames, paramValues);
                } catch (Exception e) {
                    // If JSON parsing fails, render without parameters
                    renderedSql = SqlRender.renderSql(template, new String[0], new String[0]);
                }
            } else {
                renderedSql = SqlRender.renderSql(template, new String[0], new String[0]);
            }
            
            // Then translate to target dialect
            if (dialect == null || dialect.isEmpty()) {
                dialect = "sql server";
            }
            
            String translatedSql = SqlTranslate.translateSql(renderedSql, dialect);
            LAST_RESULT.set(translatedSql);
            return CTypeConversion.toCString(translatedSql).get();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("/* sqlrender_translate error: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(pw);
            pw.println("*/");
            pw.flush();
            String msg = sw.toString();
            LAST_RESULT.set(msg);
            return CTypeConversion.toCString(msg).get();
        }
    }

    @CEntryPoint(name = "circe_check_cohort")
    public static CCharPointer checkCohort(
            @CEntryPoint.IsolateThreadContext IsolateThread thread,
            CCharPointer exprJson) {
        try {
            String expr = CTypeConversion.toJavaString(exprJson);
            CohortExpression expression = CohortExpression.fromJson(expr);

            Checker checker = new Checker();
            List<Warning> warnings = checker.check(expression);

            // Convert warnings to JSON
            ObjectMapper mapper = new ObjectMapper();
            String result = mapper.writeValueAsString(warnings);

            LAST_RESULT.set(result);
            return CTypeConversion.toCString(result).get();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("/* circe_check error: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(pw);
            pw.println("*/");
            pw.flush();
            String msg = sw.toString();
            LAST_RESULT.set(msg);
            return CTypeConversion.toCString(msg).get();
        }
    }
}
