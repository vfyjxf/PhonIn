package io.phonin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TSV loading / saving for {@link PolyphoneTable}. Kept in {@code phonin-data} so the public API
 * only exposes the table and its builder; I/O details live with the other data loaders.
 */
public final class PolyphoneTables {

    private PolyphoneTables() {}

    /** Load a table from a TSV file (UTF-8). */
    public static PolyphoneTable load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        }
    }

    /** Load a table from a TSV stream (UTF-8). */
    public static PolyphoneTable load(InputStream in) throws IOException {
        PolyphoneTable.Builder b = null;
        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] c = line.split("\t", -1);
                if (c.length < 3) continue;
                int[] cps = Codepoints.parseCodepoints(c[0]);
                if (cps == null || cps.length == 0) continue;
                String[] readings = c[2].split(",");
                if (readings.length != cps.length) continue; // count must equal codepoint count
                if (b == null) b = PolyphoneTable.builder(PhoneticSystem.mandarin);
                String[] trimmed = new String[readings.length];
                for (int i = 0; i < readings.length; i++) {
                    trimmed[i] = readings[i].trim();
                }
                b.add(cps, trimmed);
            }
        }
        return b == null ? PolyphoneTable.builder(PhoneticSystem.mandarin).build() : b.build();
    }

    /** Save a table to a TSV file (UTF-8), in the format {@link #load(Path)} reads. */
    public static void save(PolyphoneTable table, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter w =
                new BufferedWriter(
                        new OutputStreamWriter(
                                Files.newOutputStream(path), StandardCharsets.UTF_8))) {
            w.write("# PhonIn polyphone table — context-disambiguated readings");
            w.write("\n");
            w.write("# Columns: codepoints | text | readings_normalized | source");
            w.write("\n");
            for (PolyphoneTable.Entry e : table.entries()) {
                StringBuilder cps = new StringBuilder();
                for (int i = 0; i < e.codepoints.length; i++) {
                    if (i > 0) cps.append(',');
                    cps.append(Codepoints.formatCodepoint(e.codepoints[i]));
                }
                StringBuilder readings = new StringBuilder();
                for (int i = 0; i < e.readings.length; i++) {
                    if (i > 0) readings.append(',');
                    readings.append(e.readings[i]);
                }
                w.write(cps.toString());
                w.write('\t');
                w.write(Codepoints.fromCodepoints(e.codepoints));
                w.write('\t');
                w.write(readings.toString());
                w.write('\t');
                w.write("MOONBILSTM");
                w.write('\n');
            }
        }
    }
}
