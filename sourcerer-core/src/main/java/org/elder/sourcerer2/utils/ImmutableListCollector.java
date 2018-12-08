package org.elder.sourcerer2.utils;

import com.google.common.collect.ImmutableList;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class ImmutableListCollector<T>
        implements Collector<T, ImmutableList.Builder<T>, ImmutableList<T>> {
    @Override
    public Supplier<ImmutableList.Builder<T>> supplier() {
        return ImmutableList.Builder::new;
    }

    @Override
    public BiConsumer<ImmutableList.Builder<T>, T> accumulator() {
        return (acc, elem) -> acc.add(elem);
    }

    @Override
    public BinaryOperator<ImmutableList.Builder<T>> combiner() {
        return (x, y) -> x.addAll(y.build());
    }

    @Override
    public Function<ImmutableList.Builder<T>, ImmutableList<T>> finisher() {
        return ImmutableList.Builder::build;
    }

    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.noneOf(Characteristics.class);
    }
}
