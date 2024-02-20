/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.UUID;

import static org.openrewrite.ExecutionContext.CURRENT_RECIPE;

public class CastArraysAsListToList extends Recipe {

    @Override
    @NotNull
    public String getDisplayName() {
        return "Remove explicit casts on `Arrays.asList(..).toArray()`";
    }

    @Override
    @NotNull
    public String getDescription() {
        //language=markdown
        return "Convert code like `(Integer[]) Arrays.asList(1, 2, 3).toArray()` to `Arrays.asList(1, 2, 3).toArray(new Integer[0])`.";
    }

    private static final MethodMatcher ARRAYS_AS_LIST = new MethodMatcher("java.util.Arrays asList(..)", false);
    private static final MethodMatcher LIST_TO_ARRAY = new MethodMatcher("java.util.List toArray()", true);

    @Override
    @NotNull
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
            Preconditions.and(new UsesMethod<>(ARRAYS_AS_LIST), new UsesMethod<>(LIST_TO_ARRAY)),
            new CastArraysAsListToListVisitor());
    }

    private static class CastArraysAsListToListVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        @NotNull
        public J visitTypeCast(J.@NotNull TypeCast typeCast, @NotNull ExecutionContext executionContext) {
            J j = super.visitTypeCast(typeCast, executionContext);
            if (!(j instanceof J.TypeCast) || !(((J.TypeCast) j).getType() instanceof JavaType.Array)) {
                return j;
            }
            typeCast = (J.TypeCast) j;
            JavaType elementType = ((JavaType.Array) typeCast.getType()).getElemType();
            while (elementType instanceof JavaType.Array) {
                elementType = ((JavaType.Array) elementType).getElemType();
            }

            boolean matches = (elementType instanceof JavaType.Class || elementType instanceof JavaType.Parameterized)
                && ((JavaType.FullyQualified) elementType).getOwningClass() == null // does not support inner class now
                && LIST_TO_ARRAY.matches(typeCast.getExpression())
                && typeCast.getExpression() instanceof J.MethodInvocation
                && ARRAYS_AS_LIST.matches(((J.MethodInvocation) typeCast.getExpression()).getSelect());
            if (!matches) {
                return typeCast;
            }

            String fullyQualifiedName = ((JavaType.FullyQualified) elementType).getFullyQualifiedName();
            J.ArrayType castType = (J.ArrayType) typeCast.getClazz().getTree();

            if (fullyQualifiedName.equals("java.lang.Object") && !(castType.getElementType() instanceof J.ArrayType)) {
                // we don't need to fix this case because toArray() does return Object[] type
                return typeCast;
            }

            // we don't add generic type name here because generic array creation is not allowed
            StringBuilder newArrayString = new StringBuilder();
            String className = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf(".") + 1);
            newArrayString.append(className);
            newArrayString.append("[0]");
            for (TypeTree temp = castType.getElementType(); temp instanceof J.ArrayType; temp = ((J.ArrayType) temp).getElementType()) {
                newArrayString.append("[]");
            }

            JavaTemplate t = JavaTemplate
                .builder("#{any(java.util.List)}.toArray(new " + newArrayString + ")")
                .imports(fullyQualifiedName)
                .build();
            final Recipe recipe = executionContext.getMessage(CURRENT_RECIPE);
            final J result = t.apply(updateCursor(typeCast), typeCast.getCoordinates().replace(), ((J.MethodInvocation) typeCast.getExpression()).getSelect());
            final Modification marker = new Modification(UUID.randomUUID(), recipe, result);
            return result.withMarkers(new Markers(UUID.randomUUID(), Collections.singletonList(marker)));
        }
    }
}
