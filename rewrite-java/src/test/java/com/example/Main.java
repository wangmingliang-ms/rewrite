package com.example;

import org.intellij.lang.annotations.Language;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.internal.JavaTypeCache;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        @Language("java") final String code = """
            package com.example;
            
            import java.util.Arrays;
            import java.util.List;
            import java.util.stream.Collectors;
            import java.util.stream.Stream;
            
            public class Sample {
                public static void main(String[] args) {
                    final Integer[] array = (Integer[]) Arrays.asList(1, 2, 3).toArray();
                    final List<Integer> list = Stream.of(1, 2, 3).collect(Collectors.toList());
                    System.out.println(Arrays.toString(array));
                    System.out.println(list.size());
                }
            }
            """;
        final JavaParser parser = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(false)
            .typeCache(new JavaTypeCache())
            .build();
        final InMemoryExecutionContext context = new InMemoryExecutionContext(System.out::println);
        final List<SourceFile> files = parser.parse(code).toList();

//        final Path path = Paths.get("1.txt");
//        final Parser.Input input = new Parser.Input(path, () -> new ByteArrayInputStream(code.getBytes()));
//        final SourceFile file = parser.parse(input.getSource(context).readFully()).toList().get(0);
//        final List<SourceFile> files = Collections.singletonList(file);

        final InMemoryLargeSourceSet sourceSet = new InMemoryLargeSourceSet(files);

        final CastArraysAsListToList recipe = new CastArraysAsListToList();
        final RecipeRun run = recipe.run(sourceSet, context);
        final Result result = run.getChangeset().getAllResults().get(0);
        System.out.println(result.diff());
    }
}
