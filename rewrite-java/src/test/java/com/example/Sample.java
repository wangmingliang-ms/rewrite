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
