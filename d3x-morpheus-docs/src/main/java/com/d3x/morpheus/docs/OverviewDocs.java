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
package com.d3x.morpheus.docs;

import java.awt.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;
import java.util.stream.IntStream;

import com.d3x.morpheus.util.IO;
import org.testng.annotations.Test;

import com.d3x.morpheus.array.Array;
import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.range.Range;
import com.d3x.morpheus.util.text.parser.Parser;
import com.d3x.morpheus.viz.chart.Chart;

/**
 * Examples from the front overview page at http://www.zavtech.com/morpheus/docs/
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 *
 * @author  Xavier Witdouck
 */
public class OverviewDocs {


    @Test()
    public void cars93Example() {
        DataFrame.read().csv(options -> {
            options.setResource("https://www.d3xsystems.com/public/data/samples/cars93.csv");
            options.setExcludeColumnIndexes(0);
        }).rows().select(row -> {
            var weightKG = row.getDouble("Weight") * 0.453592d;
            var horsepower = row.getDouble("Horsepower");
            return horsepower / weightKG > 0.1d;
        }).cols().add("MPG(Highway/City)", Double.class, v -> {
            var cityMpg = v.row().getDouble("MPG.city");
            var highwayMpg = v.row().getDouble("MPG.highway");
            return highwayMpg / cityMpg;
        }).rows().sort(false, "MPG(Highway/City)").write().csv(options -> {
            options.setFile("/Users/witdxav/cars93m.csv");
            options.setTitle("DataFrame");
        });

        DataFrame.read().<Integer>csv(options -> {
            options.setResource("/Users/witdxav/cars93m.csv");
            options.setExcludeColumnIndexes(0);
            options.setRowKeyParser(Integer.class, values -> Integer.parseInt(values[0]));
        }).out().print(10);
    }




    @Test()
    public void regression() throws Exception {

        //Load the data
        var data = DataFrame.read().csv(options -> {
            options.setResource("https://www.d3xsystems.com/public/data/samples/cars93.csv");
            options.setExcludeColumnIndexes(0);
        });

        //Run OLS regression and plot
        var regressand = "Horsepower";
        var regressor = "EngineSize";
        data.regress().ols(regressand, regressor, true, model -> {
            IO.println(model);
            var xy = data.cols().select(regressand, regressor);
            Chart.create().withScatterPlot(xy, false, regressor, chart -> {
                chart.title().withText(regressand + " regressed on " + regressor);
                chart.subtitle().withText("Single Variable Linear Regression");
                chart.plot().style(regressand).withColor(Color.RED).withPointsVisible(true);
                chart.plot().trend(regressand).withColor(Color.BLACK);
                chart.plot().axes().domain().label().withText(regressor);
                chart.plot().axes().domain().format().withPattern("0.00;-0.00");
                chart.plot().axes().range(0).label().withText(regressand);
                chart.plot().axes().range(0).format().withPattern("0;-0");
                chart.writerPng(new File("./docs/images/ols/data-frame-ols.png"), 845, 450, true);
                chart.show();
            });
            return Optional.empty();
        });

        Thread.currentThread().join();
    }


    /**
     * Loads UK house price from the Land Registry stored in an Amazon S3 bucket
     * Note the data does not have a header, so columns will be named Column-0, Column-1 etc...
     * @param year      the year for which to load prices
     * @return          the resulting DataFrame, with some columns renamed
     */
    private DataFrame<Integer,String> loadHousePrices(Year year) {
        //final String resource = "http://prod.publicdata.landregistry.gov.uk.s3-website-eu-west-1.amazonaws.com/pp-%s.csv";
        final String resource = "/Users/witdxav/Dropbox/data/uk-house-prices/uk-house-prices-%s.csv";
        return DataFrame.read().csv(options -> {
            options.setResource(String.format(resource, year.getValue()));
            options.setHeader(false);
            options.setCharset(StandardCharsets.UTF_8);
            options.setIncludeColumnIndexes(1, 2, 4, 11);
            options.getFormats().setParser("TransactDate", Parser.ofLocalDate("yyyy-MM-dd HH:mm"));
            options.setColumnNameMapping((colName, colOrdinal) -> {
                switch (colOrdinal) {
                    case 0:     return "PricePaid";
                    case 1:     return "TransactDate";
                    case 2:     return "PropertyType";
                    case 3:     return "City";
                    default:    return colName;
                }
            });
        });
    }


    @Test()
    public void housePriceTrend() throws Exception {

        //Create a data frame to capture the median prices of Apartments in the UK'a largest cities
        var results = DataFrame.ofDoubles(
            Range.of(1995, 2015).map(Year::of),
            Array.of("LONDON", "BIRMINGHAM", "SHEFFIELD", "LEEDS", "LIVERPOOL", "MANCHESTER")
        );

        //Process yearly data in parallel to leverage all CPU cores
        results.rows().keys().parallel().forEach(year -> {
            System.out.printf("Loading UK house prices for %s...\n", year);
            var prices = loadHousePrices(year);
            prices.rows().select(row -> {
                //Filter rows to include only apartments in the relevant cities
                var propType = row.getValue("PropertyType");
                var city = (String)row.getValue("City");
                var cityUpperCase = city != null ? city.toUpperCase() : null;
                return propType != null && propType.equals("F") && results.cols().contains(cityUpperCase);
            }).rows().groupBy("City").forEach(0, (groupKey, group) -> {
                //Group row filtered frame so we can compute median prices in selected cities
                var city = (String)groupKey.item(0);
                var priceStat = group.col("PricePaid").stats().median();
                results.setDouble(year, city, priceStat);
            });
        });

        //Map row keys to LocalDates, and map values to be percentage changes from start date
        final DataFrame<LocalDate,String> plotFrame = results.mapToDoubles(v -> {
            var firstValue = v.col().getDoubleAt(0);
            var currentValue = v.getDouble();
            return (currentValue / firstValue - 1d) * 100d;
        }).rows().mapKeys(row -> {
            var year = row.key();
            return LocalDate.of(year.getValue(), 12, 31);
        });

        //Create a plot, and display it
        Chart.create().withLinePlot(plotFrame, chart -> {
            chart.title().withText("Median Nominal House Price Changes");
            chart.title().withFont(new Font("Arial", Font.BOLD, 14));
            chart.subtitle().withText("Date Range: 1995 - 2014");
            chart.plot().axes().domain().label().withText("Year");
            chart.plot().axes().range(0).label().withText("Percent Change from 1995");
            chart.plot().axes().range(0).format().withPattern("0.##'%';-0.##'%'");
            chart.plot().style("LONDON").withColor(Color.BLACK);
            chart.legend().on().bottom();
            chart.writerPng(new File("./docs/images/uk-house-prices.png"), 845, 480, true);
            chart.show();
        });

        Thread.currentThread().join();

    }


    @Test()
    public void housePriceDownload() {
        var userHome = System.getProperty("user.home");
        var url = "http://prod.publicdata.landregistry.gov.uk.s3-website-eu-west-1.amazonaws.com/pp-%1$d.csv";
        IntStream.range(1995, 2017).forEach(year -> {
            BufferedInputStream bis = null;
            BufferedOutputStream bos = null;
            try {
                var urlString = String.format(url, year);
                var fileName = String.format("uk-house-prices-%1$d.csv", year);
                var file = new File(userHome, "uk-house-prices/" + fileName);
                file.getParentFile().mkdirs();
                bos = new BufferedOutputStream(new FileOutputStream(file));
                bis = new BufferedInputStream(new URL(urlString).openStream());
                System.out.println("Downloading house prices to " + file.getAbsolutePath());
                var buffer = new byte[(int)Math.pow(1024, 2)];
                while (true) {
                    var read = bis.read(buffer);
                    if (read < 0) break;
                    else {
                        bos.write(buffer, 0, read);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                try {
                    if (bis != null) bis.close();
                    if (bos != null) bos.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

}
