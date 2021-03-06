/*
 * Copyright (C) 2014-2018 D3X Systems - All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.d3x.morpheus.perf.frame;

import java.awt.*;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.d3x.morpheus.util.IO;
import org.testng.annotations.Test;

import com.d3x.morpheus.viz.chart.Chart;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.util.PerfStat;
import com.d3x.morpheus.range.Range;

public class DataFrameCreate {

    public static void main(String[] args) {

        final int sample = 5;

        Range<Integer> rowCounts = Range.of(1,6).map(i -> i * 1000000);
        Stream<String> typeName = Stream.of("Integer", "Date", "Instant", "LocalDateTime", "ZonedDateTime");
        List<String> labels = typeName.map(s -> "DataFrame<" + s + ",?>").collect(Collectors.toList());
        DataFrame<String,String> results = DataFrame.ofDoubles(rowCounts.map(String::valueOf), labels);
        Array<String> colKeys = Array.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");

        rowCounts.forEach(rowCount -> {

            DataFrame<String,String> timing = PerfStat.run(sample, TimeUnit.MILLISECONDS, false, tasks -> {

                Range<Integer> rowIndexes = Range.of(0, rowCount.intValue());

                tasks.put("DataFrame<Integer,?>", () -> DataFrame.ofDoubles(rowIndexes, colKeys));

                tasks.put("DataFrame<Date,?>", () -> {
                    final long now = System.currentTimeMillis();
                    final Range<Date> rowKeys = rowIndexes.map(i -> new Date(now + (i * 1000)));
                    return DataFrame.ofDoubles(rowKeys, colKeys);
                });

                tasks.put("DataFrame<Instant,?>", () -> {
                    final Instant now = Instant.now();
                    final Range<Instant> rowKeys = rowIndexes.map(now::plusSeconds);
                    return DataFrame.ofDoubles(rowKeys, colKeys);
                });

                tasks.put("DataFrame<LocalDateTime,?>", () -> {
                    final Duration step = Duration.ofSeconds(1);
                    final LocalDateTime start = LocalDateTime.now().minusYears(10);
                    final LocalDateTime end = start.plusSeconds(rowCount);
                    final Range<LocalDateTime> rowKeys = Range.of(start, end, step);
                    return DataFrame.ofDoubles(rowKeys, colKeys);
                });

                tasks.put("DataFrame<ZonedDateTime,?>", () -> {
                    final Duration step = Duration.ofSeconds(1);
                    final ZonedDateTime start = ZonedDateTime.now();
                    final ZonedDateTime end = start.plusSeconds(rowCount);
                    final Range<ZonedDateTime> rowKeys = Range.of(start, end, step);
                    return DataFrame.ofDoubles(rowKeys, colKeys);
                });
            });

            String label = String.valueOf(rowCount);
            results.rows().setDouble(label, 0, timing.rows().getDouble("Mean", 0));
            results.rows().setDouble(label, 1, timing.rows().getDouble("Mean", 1));
            results.rows().setDouble(label, 2, timing.rows().getDouble("Mean", 1));
            results.rows().setDouble(label, 3, timing.rows().getDouble("Mean", 2));
            results.rows().setDouble(label, 4, timing.rows().getDouble("Mean", 2));
        });

        String initTitle = "DataFrame Create Times, 1-5 Million rows x 10 columns of Doubles (Sample " + sample + ")";
        Chart.create().withBarPlot(results, false, chart -> {
            chart.title().withText(initTitle);
            chart.title().withFont(new Font("Verdana", Font.PLAIN, 15));
            chart.plot().axes().domain().label().withText("Row Count");
            chart.plot().axes().range(0).label().withText("Time (Milliseconds)");
            chart.legend().on().bottom();
            chart.writerPng(new File("./docs/images/frame/data-frame-init-times.png"), 860, 400, true);
            chart.show();
        });
    }


    @Test()
    public void gcTimes() throws Exception {

        final int sample = 5;
        final int rowCount = 5000000;
        final Array<String> colKeys = Array.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");

        PerfStat stat1 = PerfStat.run("DataFrame<Integer,?>", sample, TimeUnit.MILLISECONDS, () -> {
            final Range<Integer> rowIndexes = Range.of(0, rowCount);
            return DataFrame.ofDoubles(rowIndexes, colKeys);
        });

        PerfStat stat2 = PerfStat.run("DataFrame<Date,?>", sample, TimeUnit.MILLISECONDS, () -> {
            final long now = System.currentTimeMillis();
            final Range<Date> rowKeys = Range.of(0, rowCount).map(i -> new Date(now + (i * 1000)));
            return DataFrame.ofDoubles(rowKeys, colKeys);
        });

        PerfStat stat3 = PerfStat.run("DataFrame<Instant,?>", sample, TimeUnit.MILLISECONDS, () -> {
            final Instant now = Instant.now();
            final Range<Instant> rowKeys = Range.of(0, rowCount).map(now::plusSeconds);
            return DataFrame.ofDoubles(rowKeys, colKeys);
        });

        PerfStat stat4 = PerfStat.run("DataFrame<LocalDateTime,?>", sample, TimeUnit.MILLISECONDS, () -> {
            final LocalDateTime start = LocalDateTime.now().minusYears(10);
            final Range<LocalDateTime> rowKeys = Range.of(0, rowCount).map(start::plusSeconds);
            return DataFrame.ofDoubles(rowKeys, colKeys);
        });

        PerfStat stat5 = PerfStat.run("DataFrame<ZonedDateTime,?>", sample, TimeUnit.MILLISECONDS, () -> {
            final ZonedDateTime start = ZonedDateTime.now();
            final Range<ZonedDateTime> rowKeys = Range.of(0, rowCount).map(start::plusSeconds);
            return DataFrame.ofDoubles(rowKeys, colKeys);
        });

        final DataFrame<String,String> results = DataFrame.combineFirst(
            stat1.getGcStats(),
            stat2.getGcStats(),
            stat3.getGcStats(),
            stat4.getGcStats(),
            stat5.getGcStats()
        );

        results.out().print();

        String initTitle = "DataFrame GC Times, 5 Million rows x 10 columns of Doubles (Sample " + sample + ")";
        Chart.create().withBarPlot(results, false, chart -> {
            chart.title().withText(initTitle);
            chart.title().withFont(new Font("Verdana", Font.PLAIN, 15));
            chart.plot().axes().domain().label().withText("Timing Statistic");
            chart.plot().axes().range(0).label().withText("Time (Milliseconds)");
            chart.legend().on().bottom();
            chart.writerPng(new File("./docs/images/frame/data-frame-gc-times.png"), 860, 400, true);
            chart.show();
        });

        Thread.currentThread().join();
    }


    @Test()
    public void slidingWindow() {
        var windowSize = 5;
        var rowKeys = Range.ofLocalDates("2014-01-01", "2014-01-11");
        var colKeys = Range.of(0, 5).map(i -> "Column-" + i);
        var frame = DataFrame.ofDoubles(rowKeys, colKeys, value -> Math.random() * 10d);
        frame.out().print();
        IO.println("\n\nPrinting sliding windows...");
        IntStream.range(windowSize-1, frame.rowCount()).mapToObj(lastRow -> {
            var startRow = lastRow - windowSize;
            return frame.rows().select(row -> row.ordinal() <= lastRow && row.ordinal() > startRow);
        }).forEach(window -> {
            ((DataFrame) window).out().print();
        });
    }


}
