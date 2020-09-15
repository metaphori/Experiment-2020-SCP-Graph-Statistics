/*
 * Copyright (C) 2010-2019, Danilo Pianini and contributors listed in the main project's alchemist/build.gradle file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */
package it.unibo.alchemist.loader.export;

import com.google.common.base.Charsets;
import it.unibo.alchemist.boundary.interfaces.OutputMonitor;
import it.unibo.alchemist.model.implementations.times.DoubleTime;
import it.unibo.alchemist.model.interfaces.Environment;
import it.unibo.alchemist.model.interfaces.Position;
import it.unibo.alchemist.model.interfaces.Reaction;
import it.unibo.alchemist.model.interfaces.Time;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Writes on file data provided by a number of {@link Extractor}s. Produces a
 * CSV with '#' as comment character. Even though this class implements
 * {@link OutputMonitor}, it is not {@link java.io.Serializable}.
 *
 * @param <T> Concentration type
 * @param <P> {@link Position} type position type
 */
@SuppressWarnings("serial")
public final class Exporter<T, P extends Position<? extends P>> implements OutputMonitor<T, P> {

    private static final String SEPARATOR = "#####################################################################";
    private final double sampleSpace;
    private final String header;
    private final PrintStream out;
    private final List<Extractor> extractors;
    private long count = -1L; // The 0th should be sampled

    /**
     * @param target the target file
     * @param space the sampling space, namely how many simulated time units the {@link Exporter} should log
     * @param header a message to be inserted in the header of the file.
     * @param columns the extractors to use
     * @throws FileNotFoundException if the file can not be opened for writing
     */
    public Exporter(
            final String target,
            final double space,
            final String header,
            final List<Extractor> columns
    ) throws FileNotFoundException {
        this.sampleSpace = space;
        try {
            out = new PrintStream(target, Charsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("There is a bug in Alchemist, in " + getClass(), e);
        }
        extractors = columns;
        this.header = header;
    }

    @Override
    public void finished(final Environment<T, P> environment, final Time time, final long step) {
        out.println(SEPARATOR);
        out.print("# End of data export. Simulation finished at: ");
        final SimpleDateFormat isoTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ", Locale.US);
        isoTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        out.print(isoTime.format(new Date()));
        out.println(" #");
        out.println(SEPARATOR);
        out.close();
    }

    @Override
    public void initialized(final Environment<T, P> environment) {
        out.println(SEPARATOR);
        out.print("# Alchemist log file - simulation started at: ");
        final SimpleDateFormat isoTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ", Locale.US);
        isoTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        out.print(isoTime.format(new Date()));
        out.println(" #");
        out.println(SEPARATOR);
        out.print("# ");
        out.println(header);
        out.println("#");
        out.println("# The columns have the following meaning: ");
        out.print("# ");
        extractors.stream()
                .flatMap(e -> e.getNames().stream())
                .forEach(name -> {
                    out.print(name);
                    out.print(" ");
                });
        out.println();
        stepDone(environment, null, new DoubleTime(), 0);
    }

    @Override
    public void stepDone(final Environment<T, P> environment, final Reaction<T> reaction, final Time time, final long step) {
        final long curSample = (long) (time.toDouble() / sampleSpace);
        if (curSample > count) {
            count = curSample;
            writeRow(environment, reaction, time, step);
        }
    }

    private void printDatum(final double datum) {
        out.print(datum);
        out.print(' ');
    }

    private void writeRow(final Environment<T, ?> env, final Reaction<T> r, final Time time, final long step) {
        extractors.stream()
                .flatMapToDouble(e -> Arrays.stream(e.extractData(env, r, time, step)))
                .forEach(this::printDatum);
        out.println();
    }

}
