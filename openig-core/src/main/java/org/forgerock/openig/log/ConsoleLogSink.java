/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.log;

import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.util.ISO8601;

/**
 * A sink that writes log entries to the standard error stream.
 */
public class ConsoleLogSink implements LogSink {

    /** The level of log entries to display in the console (default: {@link LogLevel#INFO INFO}). */
    private LogLevel level = LogLevel.INFO;

    /**
     * Sets the level of log entries to display in the console.
     * @param level level of log entries to display in the console
     */
    public void setLevel(final LogLevel level) {
        this.level = level;
    }

    @Override
    public void log(LogEntry entry) {
        if (isLoggable(entry.getSource(), entry.getLevel())) {
            synchronized (this) {
                StringBuilder sb = new StringBuilder();
                sb.append(ISO8601.format(entry.getTime())).append(':').append(entry.getSource()).append(':');
                sb.append(entry.getLevel()).append(':').append(entry.getMessage());
                if (entry.getData() != null) {
                    sb.append(':').append(entry.getData().toString());
                }
                System.err.println(sb.toString());
                System.err.flush();
            }
        }
    }

    @Override
    public boolean isLoggable(String source, LogLevel level) {
        return (level.compareTo(this.level) >= 0);
    }

    /**
     * Creates and initializes a console sink in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            ConsoleLogSink sink = new ConsoleLogSink();
            sink.setLevel(config.get("level").defaultTo(sink.level.toString()).asEnum(LogLevel.class));
            return sink;
        }
    }
}
