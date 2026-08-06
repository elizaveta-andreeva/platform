package test.de.iip_ecosphere.platform.configuration.easyProducer.opcua;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import de.iip_ecosphere.platform.configuration.easyProducer.opcua.parser.DomParser;

/**
 * Benchmarks DomParser on all NodeSets in src/test/resources/NodeSets/
 * (excluding RequiredModels subfolder).
 *
 * Outputs results to target/benchmark/benchmark_results.csv
 */
@RunWith(Parameterized.class)
public class DomParserBenchmarkTest {

    private static final File NODESET_DIR = new File("src/test/resources/NodeSets");
    private static final File OUT_DIR     = new File("target/benchmark");
    private static final File CSV_FILE    = new File(OUT_DIR, "benchmark_results.csv");
    private static final int  RUNS        = 3;
    private static final Charset UTF8     = Charset.forName("UTF-8");

    private static final List<String[]> rows = new ArrayList<>();

    private final File nodeSetFile;

    public DomParserBenchmarkTest(File nodeSetFile) {
        this.nodeSetFile = nodeSetFile;
    }


    @Parameters(name = "{0}")
    public static List<Object[]> nodesets() {
        List<Object[]> params = new ArrayList<>();
        File[] files = NODESET_DIR.listFiles(f ->
            f.isFile() && f.getName().toLowerCase().endsWith(".xml"));
        if (files != null) {
            for (File f : files) {
                params.add(new Object[]{f});
            }
        }
        return params;
    }


    @BeforeClass
    public static void setup() throws IOException {
        OUT_DIR.mkdirs();        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE, false))) {
            pw.println("NodeSet,FileSizeKB,UAObjectTypeCountIn,"
                + "Run1Ms,Run2Ms,Run3Ms,AvgMs,"
                + "IvmlLines,UnknownDataTypes,"
                + "IvmlElements_RootObjectType,IvmlElements_FieldVariableType,"
                + "IvmlElements_EnumType,IvmlElements_ObjectTypeType,"
                + "InputLines,InputOutputRatio,"
                + "CheckRequiredModels,CheckRedundancy");
        }
        DomParser.setDefaultVerbose(false);
    }

    @AfterClass
    public static void writeResults() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            for (String[] row : rows) {
                pw.println(String.join(",", row));
            }
        }
        System.out.println("\nBenchmark complete. Results: " + CSV_FILE.getAbsolutePath());
    }

    @Test
    public void benchmark() throws IOException {
        String name = nodeSetFile.getName().replace(".xml", "").replace(".XML", "");
        File outFile = new File(OUT_DIR, name + ".ivml");

        long fileSizeKB = nodeSetFile.length() / 1024;
        String xmlContent = new String(Files.readAllBytes(nodeSetFile.toPath()), UTF8);
        int uaObjectTypeCountIn = countOccurrences(xmlContent, "<UAObjectType ");
        int inputLines = xmlContent.split("\n").length;

        long[] times = new long[RUNS];
        boolean parseOk = true;
        String checkRequiredModels = "OK";
        String checkRedundancy    = "OK";

        for (int i = 0; i < RUNS; i++) {
            try {
                DomParser.setUsingIvmlFolder(OUT_DIR.getPath());
                long start = System.currentTimeMillis();
                DomParser.process(nodeSetFile, name, outFile, false);
                times[i] = System.currentTimeMillis() - start;
            } catch (Exception e) {
                times[i] = -1;
                parseOk = false;
                if (e.getMessage() != null && e.getMessage().contains("checkRequiredModels")) {
                    checkRequiredModels = "FAIL: " + e.getMessage().replace(",", ";");
                } else if (e.getMessage() != null && e.getMessage().contains("checkRedundancy")) {
                    checkRedundancy = "FAIL: " + e.getMessage().replace(",", ";");
                } else {
                    checkRequiredModels = "FAIL: " + e.getClass().getSimpleName();
                }
            }
        }

        long avgMs = parseOk
            ? (times[0] + times[1] + times[2]) / 3
            : -1;

        int ivmlLines        = 0;
        int unknownTypes     = 0;
        int rootObjectType   = 0;
        int fieldVariableType= 0;
        int enumType         = 0;
        int objectTypeType   = 0;
        double ioRatio       = 0;

        if (parseOk && outFile.exists()) {
            String ivmlContent = new String(Files.readAllBytes(outFile.toPath()), UTF8);
            ivmlLines         = ivmlContent.split("\n").length;
            unknownTypes      = countOccurrences(ivmlContent, "opcUnknownDataType");
            rootObjectType    = countOccurrences(ivmlContent, "UARootObjectType");
            fieldVariableType = countOccurrences(ivmlContent, "UAFieldVariableType");
            enumType          = countOccurrences(ivmlContent, "UAEnumType");
            objectTypeType    = countOccurrences(ivmlContent, "UAObjectTypeType");
            ioRatio           = inputLines > 0
                ? Math.round((double) ivmlLines / inputLines * 100.0) / 100.0
                : 0;
        }

        String[] row = {
            nodeSetFile.getName(),
            String.valueOf(fileSizeKB),
            String.valueOf(uaObjectTypeCountIn),
            String.valueOf(times[0]),
            String.valueOf(times[1]),
            String.valueOf(times[2]),
            String.valueOf(avgMs),
            String.valueOf(ivmlLines),
            String.valueOf(unknownTypes),
            String.valueOf(rootObjectType),
            String.valueOf(fieldVariableType),
            String.valueOf(enumType),
            String.valueOf(objectTypeType),
            String.valueOf(inputLines),
            String.valueOf(ioRatio),
            checkRequiredModels,
            checkRedundancy
        };

        synchronized (rows) {
            rows.add(row);
        }

        System.out.printf("%-70s | avg=%4d ms | ivml=%5d lines | unknown=%d%n",
            nodeSetFile.getName(), avgMs, ivmlLines, unknownTypes);
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}