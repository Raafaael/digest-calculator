import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Testes de integracao autocontidos, sem dependencia de JUnit. */
public final class DigestCalculatorTest {
    private static int passed;
    private static int failed;

    private DigestCalculatorTest() {
    }

    public static void main(String[] args) throws Exception {
        execute("fluxo completo e persistencia", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testCompleteWorkflowAndPersistence();
            }
        });
        execute("quatro algoritmos e arquivo vazio", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testAllAlgorithmsAndEmptyFile();
            }
        });
        execute("arquivo binario maior que o buffer", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testLargeBinaryFile();
            }
        });
        execute("validacao dos argumentos e caminhos", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testArgumentAndPathValidation();
            }
        });
        execute("lista vazia equivale a catalogo sem entradas", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testEmptyCatalogFiles();
            }
        });
        execute("entradas problematicas geram aviso sem abortar", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testLenientCatalogEntries();
            }
        });
        execute("formatacao do XML regravado", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testSavedXmlFormatting();
            }
        });
        execute("validacao estrutural e seguranca do XML", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testXmlValidationAndXxeRejection();
            }
        });
        execute("main em processo Java separado", new CheckedTest() {
            @Override
            public void run() throws Exception {
                testMainInSeparateProcess();
            }
        });

        System.out.println();
        System.out.println("Resumo: " + passed + " teste(s) aprovado(s), "
                + failed + " teste(s) reprovado(s).");
        if (failed != 0) {
            throw new AssertionError("Existem testes reprovados.");
        }
    }

    private static void testCompleteWorkflowAndPersistence() throws Exception {
        Path root = Files.createTempDirectory("digest-calculator-core-");
        try {
            Path filesDirectory = Files.createDirectory(root.resolve("pasta com espacos"));
            Path catalog = root.resolve("catalogo com espacos.xml");

            writeUtf8(filesDirectory.resolve("ok.txt"), "conteudo-ok");
            writeUtf8(filesDirectory.resolve("not-ok.txt"), "conteudo-atualizado");
            writeUtf8(filesDirectory.resolve("existing-no-sha256.txt"),
                    "conteudo-existente");
            writeUtf8(filesDirectory.resolve("new.txt"), "conteudo-novo-unico");
            Files.write(filesDirectory.resolve("dup-a.bin"), new byte[] {0, 1, 2, 3, 4});
            Files.write(filesDirectory.resolve("dup-b.bin"), new byte[] {0, 1, 2, 3, 4});
            writeUtf8(filesDirectory.resolve("catalog-collision.bin"),
                    "conteudo-colisao-catalogo");

            String okDigest = digest("SHA-256", bytes("conteudo-ok"));
            String oldNotOkDigest = digest("SHA-256", bytes("conteudo-antigo"));
            String existingMd5 = digest("MD5", bytes("conteudo-existente"));
            String catalogCollisionDigest = digest("SHA-256",
                    bytes("conteudo-colisao-catalogo"));

            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<CATALOG>\n"
                    + fileEntry("ok.txt", "SHA256", okDigest)
                    + fileEntry("not-ok.txt", "SHA256", oldNotOkDigest)
                    + fileEntry("existing-no-sha256.txt", "MD5", existingMd5)
                    + fileEntry("absent-name.bin", "SHA256", catalogCollisionDigest)
                    + "</CATALOG>\n";
            writeUtf8(catalog, xml);

            Invocation first = invoke("SHA256", catalog.toString(), filesDirectory.toString());
            assertEquals(0, first.exitCode, "codigo de saida da primeira execucao");
            assertEquals("", first.stderr, "stderr da primeira execucao");

            String duplicateDigest = digest("SHA-256", new byte[] {0, 1, 2, 3, 4});
            String existingSha256 = digest("SHA-256", bytes("conteudo-existente"));
            String newSha256 = digest("SHA-256", bytes("conteudo-novo-unico"));
            String currentNotOkDigest = digest("SHA-256", bytes("conteudo-atualizado"));
            String expectedFirst = line("catalog-collision.bin", "SHA256",
                    catalogCollisionDigest, "COLISION")
                    + line("dup-a.bin", "SHA256", duplicateDigest, "COLISION")
                    + line("dup-b.bin", "SHA256", duplicateDigest, "COLISION")
                    + line("existing-no-sha256.txt", "SHA256", existingSha256,
                            "NOT FOUND")
                    + line("new.txt", "SHA256", newSha256, "NOT FOUND")
                    + line("not-ok.txt", "SHA256", currentNotOkDigest, "NOT OK")
                    + line("ok.txt", "SHA256", okDigest, "OK");
            assertEquals(expectedFirst, first.stdout, "saida completa da primeira execucao");

            Document updated = parse(catalog);
            assertEquals(existingSha256,
                    findDigest(updated, "existing-no-sha256.txt", "SHA256"),
                    "digest acrescentado ao FILE_ENTRY existente");
            assertEquals(newSha256, findDigest(updated, "new.txt", "SHA256"),
                    "digest acrescentado em novo FILE_ENTRY");
            assertNull(findFileEntry(updated, "catalog-collision.bin"),
                    "colisao com catalogo nao pode ser incluida");
            assertNull(findFileEntry(updated, "dup-a.bin"),
                    "colisao na pasta nao pode ser incluida");
            assertEquals(oldNotOkDigest, findDigest(updated, "not-ok.txt", "SHA256"),
                    "registro NOT OK deve permanecer inalterado");
            assertEquals("new.txt", lastFileEntryName(updated),
                    "novo arquivo deve ser incluido no fim do catalogo");

            byte[] beforeSecondRun = Files.readAllBytes(catalog);
            Invocation second = invoke("SHA256", catalog.toString(), filesDirectory.toString());
            assertEquals(0, second.exitCode, "codigo de saida da segunda execucao");
            assertEquals("", second.stderr, "stderr da segunda execucao");
            String expectedSecond = line("catalog-collision.bin", "SHA256",
                    catalogCollisionDigest, "COLISION")
                    + line("dup-a.bin", "SHA256", duplicateDigest, "COLISION")
                    + line("dup-b.bin", "SHA256", duplicateDigest, "COLISION")
                    + line("existing-no-sha256.txt", "SHA256", existingSha256, "OK")
                    + line("new.txt", "SHA256", newSha256, "OK")
                    + line("not-ok.txt", "SHA256", currentNotOkDigest, "NOT OK")
                    + line("ok.txt", "SHA256", okDigest, "OK");
            assertEquals(expectedSecond, second.stdout,
                    "NOT FOUND deve se tornar OK na segunda execucao");
            assertTrue(Arrays.equals(beforeSecondRun, Files.readAllBytes(catalog)),
                    "catalogo nao deve ser regravado quando nao houver NOT FOUND");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testAllAlgorithmsAndEmptyFile() throws Exception {
        String[][] algorithms = {
            {"MD5", "MD5"},
            {"SHA1", "SHA-1"},
            {"SHA256", "SHA-256"},
            {"SHA512", "SHA-512"}
        };
        Path root = Files.createTempDirectory("digest-calculator-algorithms-");
        try {
            for (String[] algorithm : algorithms) {
                Path caseRoot = Files.createTempDirectory(root, algorithm[0].toLowerCase() + "-");
                Path folder = Files.createDirectory(caseRoot.resolve("files"));
                Path file = Files.createFile(folder.resolve("empty.bin"));
                Path catalog = caseRoot.resolve("catalog.xml");
                writeUtf8(catalog, "<CATALOG/>\n");

                Invocation invocation = invoke(algorithm[0], catalog.toString(),
                        folder.toString());
                String expectedDigest = digest(algorithm[1], Files.readAllBytes(file));
                assertEquals(0, invocation.exitCode,
                        "codigo de saida para " + algorithm[0]);
                assertEquals(line("empty.bin", algorithm[0], expectedDigest, "NOT FOUND"),
                        invocation.stdout, "digest vazio para " + algorithm[0]);
                assertEquals(expectedDigest,
                        findDigest(parse(catalog), "empty.bin", algorithm[0]),
                        "persistencia para " + algorithm[0]);
            }
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testLargeBinaryFile() throws Exception {
        Path root = Files.createTempDirectory("digest-calculator-binary-");
        try {
            Path folder = Files.createDirectory(root.resolve("files"));
            Path catalog = root.resolve("catalog.xml");
            byte[] content = new byte[BUFFER_SIZE_FOR_TEST * 3 + 137];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i * 31 + 7);
            }
            Files.write(folder.resolve("large.bin"), content);
            String expectedDigest = digest("SHA-512", content);
            writeUtf8(catalog, "<CATALOG>\n"
                    + fileEntry("large.bin", "SHA512", expectedDigest.toUpperCase())
                    + "</CATALOG>\n");

            Invocation invocation = invoke("sha512", catalog.toString(), folder.toString());
            assertEquals(0, invocation.exitCode, "codigo para arquivo binario grande");
            assertEquals(line("large.bin", "SHA512", expectedDigest, "OK"),
                    invocation.stdout, "leitura em multiplos blocos");
            assertEquals("", invocation.stderr, "stderr para arquivo binario grande");
        } finally {
            deleteRecursively(root);
        }
    }

    private static final int BUFFER_SIZE_FOR_TEST = 8192;

    private static void testArgumentAndPathValidation() throws Exception {
        Invocation missingArguments = invoke();
        assertEquals(1, missingArguments.exitCode, "argumentos ausentes");
        assertContains(missingArguments.stdout, "Uso: java DigestCalculator",
                "orientacao de uso");
        assertEquals("", missingArguments.stderr, "argumentos ausentes nao sao erro interno");

        Invocation invalidAlgorithm = invoke("CRC32", "x", "y");
        assertEquals(1, invalidAlgorithm.exitCode, "algoritmo invalido");
        assertContains(invalidAlgorithm.stderr, "tipo de digest invalido",
                "mensagem de algoritmo invalido");

        Path root = Files.createTempDirectory("digest-calculator-paths-");
        try {
            Path folder = Files.createDirectory(root.resolve("files"));
            Path catalog = root.resolve("catalog.xml");
            writeUtf8(catalog, "<CATALOG/>\n");

            Invocation missingCatalog = invoke("SHA256",
                    root.resolve("missing.xml").toString(), folder.toString());
            assertEquals(1, missingCatalog.exitCode, "catalogo inexistente");
            assertContains(missingCatalog.stderr, "catalogo nao existe",
                    "mensagem para catalogo inexistente");

            Invocation missingFolder = invoke("SHA256", catalog.toString(),
                    root.resolve("missing-folder").toString());
            assertEquals(1, missingFolder.exitCode, "pasta inexistente");
            assertContains(missingFolder.stderr, "pasta de arquivos nao existe",
                    "mensagem para pasta inexistente");

            byte[] catalogBeforeEmptyRun = Files.readAllBytes(catalog);
            Invocation emptyFolder = invoke("SHA256", catalog.toString(), folder.toString());
            assertEquals(0, emptyFolder.exitCode, "pasta vazia e valida");
            assertEquals("", emptyFolder.stdout, "pasta vazia nao gera linhas");
            assertTrue(Arrays.equals(catalogBeforeEmptyRun, Files.readAllBytes(catalog)),
                    "pasta vazia nao regrava catalogo");

            Path catalogInsideFolder = folder.resolve("inside.xml");
            writeUtf8(catalogInsideFolder, "<CATALOG/>\n");
            writeUtf8(folder.resolve("data.txt"), "dados-da-pasta");
            String dataDigest = digest("SHA-256", bytes("dados-da-pasta"));
            Invocation inside = invoke("SHA256", catalogInsideFolder.toString(),
                    folder.toString());
            assertEquals(0, inside.exitCode, "catalogo dentro da pasta deve ser aceito");
            assertEquals("", inside.stderr, "stderr com catalogo dentro da pasta");
            assertEquals(line("data.txt", "SHA256", dataDigest, "NOT FOUND"), inside.stdout,
                    "o proprio catalogo nao deve ser processado");
            Document insideCatalog = parse(catalogInsideFolder);
            assertEquals(dataDigest, findDigest(insideCatalog, "data.txt", "SHA256"),
                    "digest gravado no catalogo que esta dentro da pasta");
            assertNull(findFileEntry(insideCatalog, "inside.xml"),
                    "catalogo nao deve registrar a si mesmo");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testEmptyCatalogFiles() throws Exception {
        Path root = Files.createTempDirectory("digest-calculator-empty-");
        try {
            Path folder = Files.createDirectory(root.resolve("files"));
            writeUtf8(folder.resolve("a.txt"), "conteudo-a");
            String expectedDigest = digest("MD5", bytes("conteudo-a"));
            byte[][] contents = {
                new byte[0],
                bytes(" \r\n\t\n"),
                new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}
            };
            for (int i = 0; i < contents.length; i++) {
                Path catalog = root.resolve("empty-" + i + ".xml");
                Files.write(catalog, contents[i]);
                Invocation invocation = invoke("MD5", catalog.toString(), folder.toString());
                assertEquals(0, invocation.exitCode, "codigo de saida da lista vazia " + i);
                assertEquals("", invocation.stderr, "stderr da lista vazia " + i);
                assertEquals(line("a.txt", "MD5", expectedDigest, "NOT FOUND"),
                        invocation.stdout, "saida com lista vazia " + i);
                assertEquals(expectedDigest, findDigest(parse(catalog), "a.txt", "MD5"),
                        "lista vazia deve receber o novo FILE_ENTRY " + i);
            }
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testLenientCatalogEntries() throws Exception {
        Path root = Files.createTempDirectory("digest-calculator-lenient-");
        try {
            Path folder = Files.createDirectory(root.resolve("files"));
            Path catalog = root.resolve("catalog.xml");
            writeUtf8(folder.resolve("dup.txt"), "conteudo-dup");
            writeUtf8(folder.resolve("hex.txt"), "conteudo-hex");
            writeUtf8(folder.resolve("rep.txt"), "conteudo-rep");
            writeUtf8(folder.resolve("tipo.txt"), "conteudo-tipo");

            String dupDigest = digest("SHA-256", bytes("conteudo-dup"));
            String hexDigest = digest("SHA-256", bytes("conteudo-hex"));
            String repDigest = digest("SHA-256", bytes("conteudo-rep"));
            String tipoDigest = digest("SHA-256", bytes("conteudo-tipo"));
            writeUtf8(catalog, "<CATALOG>\n"
                    + fileEntry("dup.txt", "MD5", digest("MD5", bytes("conteudo-dup")))
                    + fileEntry("hex.txt", "SHA256", "123abc")
                    + "  <FILE_ENTRY>\n"
                    + "    <FILE_NAME>rep.txt</FILE_NAME>\n"
                    + "    " + digestEntry("SHA256", repDigest) + "\n"
                    + "    " + digestEntry("SHA256", digest("SHA-256", bytes("outro"))) + "\n"
                    + "  </FILE_ENTRY>\n"
                    + fileEntry("tipo.txt", "SHA-256", tipoDigest)
                    + fileEntry("dup.txt", "SHA256", dupDigest)
                    + "</CATALOG>\n");

            Invocation invocation = invoke("SHA256", catalog.toString(), folder.toString());
            assertEquals(0, invocation.exitCode, "entradas problematicas nao abortam");
            String expected = line("dup.txt", "SHA256", dupDigest, "OK")
                    + line("hex.txt", "SHA256", hexDigest, "NOT OK")
                    + line("rep.txt", "SHA256", repDigest, "OK")
                    + line("tipo.txt", "SHA256", tipoDigest, "NOT FOUND");
            assertEquals(expected, invocation.stdout, "saida com entradas problematicas");
            assertContains(invocation.stderr, "DIGEST_HEX invalido para hex.txt",
                    "aviso de DIGEST_HEX invalido");
            assertContains(invocation.stderr, "DIGEST_TYPE invalido ignorado para tipo.txt",
                    "aviso de DIGEST_TYPE invalido");
            assertContains(invocation.stderr, "SHA256 repetido para rep.txt",
                    "aviso de tipo repetido");
            assertTrue(!invocation.stderr.contains("dup.txt"),
                    "FILE_NAME repetido sem conflito nao gera aviso");

            Document updated = parse(catalog);
            assertEquals(tipoDigest, findDigest(updated, "tipo.txt", "SHA256"),
                    "NOT FOUND gravado mesmo com DIGEST_TYPE invalido no registro");
            assertEquals("123abc", findDigest(updated, "hex.txt", "SHA256"),
                    "DIGEST_HEX invalido nao deve ser substituido");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testSavedXmlFormatting() throws Exception {
        Path root = Files.createTempDirectory("digest-calculator-format-");
        try {
            Path folder = Files.createDirectory(root.resolve("files"));
            Path catalog = root.resolve("catalog.xml");
            writeUtf8(folder.resolve("Arquivo1.dat"), "conteudo-1");
            writeUtf8(folder.resolve("Arquivo2.dat"), "conteudo-2");
            String sha1 = digest("SHA-1", bytes("conteudo-1"));
            /* Layout do exemplo do enunciado: tabs e espaco antes do hex. */
            writeUtf8(catalog, "<CATALOG>\n"
                    + "\t<FILE_ENTRY>\n"
                    + "\t\t<FILE_NAME>Arquivo1.dat</FILE_NAME>\n"
                    + "\t\t<DIGEST_ENTRY>\n"
                    + "\t\t\t<DIGEST_TYPE>SHA1</DIGEST_TYPE>\n"
                    + "\t\t\t<DIGEST_HEX> " + sha1 + "</DIGEST_HEX>\n"
                    + "\t\t</DIGEST_ENTRY>\n"
                    + "\t</FILE_ENTRY>\n"
                    + "</CATALOG>\n");

            for (String type : new String[] {"SHA256", "MD5"}) {
                Invocation invocation = invoke(type, catalog.toString(), folder.toString());
                assertEquals(0, invocation.exitCode, "codigo de saida com " + type);
                assertEquals("", invocation.stderr, "stderr com " + type);
            }

            String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<CATALOG>\n"
                    + "    <FILE_ENTRY>\n"
                    + "        <FILE_NAME>Arquivo1.dat</FILE_NAME>\n"
                    + formattedDigestEntry("SHA1", " " + sha1)
                    + formattedDigestEntry("SHA256", digest("SHA-256", bytes("conteudo-1")))
                    + formattedDigestEntry("MD5", digest("MD5", bytes("conteudo-1")))
                    + "    </FILE_ENTRY>\n"
                    + "    <FILE_ENTRY>\n"
                    + "        <FILE_NAME>Arquivo2.dat</FILE_NAME>\n"
                    + formattedDigestEntry("SHA256", digest("SHA-256", bytes("conteudo-2")))
                    + formattedDigestEntry("MD5", digest("MD5", bytes("conteudo-2")))
                    + "    </FILE_ENTRY>\n"
                    + "</CATALOG>";
            String actual = new String(Files.readAllBytes(catalog), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(expected, actual, "XML regravado com indentacao uniforme");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testXmlValidationAndXxeRejection() throws Exception {
        Path root = Files.createTempDirectory("digest-calculator-xml-");
        try {
            Path folder = Files.createDirectory(root.resolve("files"));
            writeUtf8(folder.resolve("file.txt"), "conteudo");

            Path wrongRoot = root.resolve("wrong-root.xml");
            writeUtf8(wrongRoot, "<WRONG/>\n");
            byte[] wrongRootBefore = Files.readAllBytes(wrongRoot);
            Invocation wrongRootResult = invoke("SHA256", wrongRoot.toString(),
                    folder.toString());
            assertEquals(2, wrongRootResult.exitCode, "raiz XML invalida");
            assertContains(wrongRootResult.stderr, "tag raiz deve ser CATALOG",
                    "mensagem de raiz invalida");
            assertTrue(Arrays.equals(wrongRootBefore, Files.readAllBytes(wrongRoot)),
                    "XML invalido deve permanecer inalterado");

            Path missingHex = root.resolve("missing-hex.xml");
            writeUtf8(missingHex, "<CATALOG><FILE_ENTRY><FILE_NAME>file.txt</FILE_NAME>"
                    + "<DIGEST_ENTRY><DIGEST_TYPE>SHA256</DIGEST_TYPE></DIGEST_ENTRY>"
                    + "</FILE_ENTRY></CATALOG>");
            byte[] missingHexBefore = Files.readAllBytes(missingHex);
            Invocation missingHexResult = invoke("SHA256", missingHex.toString(),
                    folder.toString());
            assertEquals(2, missingHexResult.exitCode, "DIGEST_ENTRY sem DIGEST_HEX");
            assertContains(missingHexResult.stderr, "exatamente um DIGEST_HEX",
                    "mensagem de DIGEST_HEX ausente");
            assertTrue(Arrays.equals(missingHexBefore, Files.readAllBytes(missingHex)),
                    "catalogo estruturalmente invalido deve permanecer inalterado");

            Path secret = root.resolve("secret.txt");
            writeUtf8(secret, "NAO_DEVE_SER_LIDO");
            Path xxe = root.resolve("xxe.xml");
            String secretUri = secret.toUri().toASCIIString();
            writeUtf8(xxe, "<?xml version=\"1.0\"?>"
                    + "<!DOCTYPE CATALOG [<!ENTITY xxe SYSTEM \"" + secretUri + "\">]>"
                    + "<CATALOG><FILE_ENTRY><FILE_NAME>&xxe;</FILE_NAME>"
                    + digestEntry("SHA256", digest("SHA-256", bytes("conteudo")))
                    + "</FILE_ENTRY></CATALOG>");
            byte[] xxeBefore = Files.readAllBytes(xxe);
            Invocation xxeResult = invoke("SHA256", xxe.toString(), folder.toString());
            assertEquals(2, xxeResult.exitCode, "DOCTYPE/XXE deve ser rejeitado");
            assertTrue(!xxeResult.stderr.contains("NAO_DEVE_SER_LIDO"),
                    "conteudo externo nao deve aparecer na mensagem");
            assertTrue(Arrays.equals(xxeBefore, Files.readAllBytes(xxe)),
                    "catalogo com XXE deve permanecer inalterado");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testMainInSeparateProcess() throws Exception {
        ProcessInvocation withoutArguments = invokeMain();
        assertEquals(1, withoutArguments.exitCode,
                "codigo real do processo sem argumentos");
        assertContains(withoutArguments.stdout, "Uso: java DigestCalculator",
                "uso impresso pelo main real");
        assertEquals("", withoutArguments.stderr,
                "stderr do processo real sem argumentos");

        Path root = Files.createTempDirectory("digest-calculator-process-");
        try {
            Path folder = Files.createDirectory(root.resolve("files"));
            Path catalog = root.resolve("catalog.xml");
            writeUtf8(folder.resolve("main.txt"), "processo-separado");
            writeUtf8(catalog, "<CATALOG/>\n");

            String expectedDigest = digest("SHA-1", bytes("processo-separado"));
            ProcessInvocation valid = invokeMain("SHA1", catalog.toString(),
                    folder.toString());
            assertEquals(0, valid.exitCode, "codigo real do processo valido");
            assertEquals(line("main.txt", "SHA1", expectedDigest, "NOT FOUND"),
                    valid.stdout, "stdout do processo Java real");
            assertEquals("", valid.stderr, "stderr do processo Java real");
            assertEquals(expectedDigest, findDigest(parse(catalog), "main.txt", "SHA1"),
                    "processo real deve persistir o digest");
        } finally {
            deleteRecursively(root);
        }
    }

    private static Invocation invoke(String... args) throws Exception {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        PrintStream stdout = new PrintStream(stdoutBytes, true, "UTF-8");
        PrintStream stderr = new PrintStream(stderrBytes, true, "UTF-8");
        int exitCode;
        try {
            exitCode = DigestCalculator.run(args, stdout, stderr);
        } finally {
            stdout.flush();
            stderr.flush();
            stdout.close();
            stderr.close();
        }
        return new Invocation(exitCode, stdoutBytes.toString("UTF-8"),
                stderrBytes.toString("UTF-8"));
    }

    private static ProcessInvocation invokeMain(String... args) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"),
                "bin", windows ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("DigestCalculator");
        command.addAll(Arrays.asList(args));

        Process process = new ProcessBuilder(command).start();
        String stdout = readUtf8(process.getInputStream());
        String stderr = readUtf8(process.getErrorStream());
        int exitCode = process.waitFor();
        return new ProcessInvocation(exitCode, stdout, stderr);
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toString("UTF-8");
    }

    private static String fileEntry(String fileName, String digestType, String digestHex) {
        return "  <FILE_ENTRY>\n"
                + "    <FILE_NAME>" + fileName + "</FILE_NAME>\n"
                + "    " + digestEntry(digestType, digestHex) + "\n"
                + "  </FILE_ENTRY>\n";
    }

    private static String digestEntry(String digestType, String digestHex) {
        return "<DIGEST_ENTRY><DIGEST_TYPE>" + digestType + "</DIGEST_TYPE>"
                + "<DIGEST_HEX>" + digestHex + "</DIGEST_HEX></DIGEST_ENTRY>";
    }

    private static String formattedDigestEntry(String digestType, String digestHex) {
        return "        <DIGEST_ENTRY>\n"
                + "            <DIGEST_TYPE>" + digestType + "</DIGEST_TYPE>\n"
                + "            <DIGEST_HEX>" + digestHex + "</DIGEST_HEX>\n"
                + "        </DIGEST_ENTRY>\n";
    }

    private static String line(String fileName, String type, String digest, String status) {
        return fileName + " " + type + " " + digest + " (" + status + ")"
                + System.lineSeparator();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void writeUtf8(Path path, String value) throws IOException {
        Files.write(path, bytes(value));
    }

    private static String digest(String algorithm, byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance(algorithm).digest(content);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static Document parse(Path path) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
    }

    private static Element findFileEntry(Document document, String fileName) {
        NodeList entries = document.getDocumentElement().getElementsByTagName("FILE_ENTRY");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            NodeList names = entry.getElementsByTagName("FILE_NAME");
            if (names.getLength() == 1
                    && fileName.equals(names.item(0).getTextContent().trim())) {
                return entry;
            }
        }
        return null;
    }

    private static String findDigest(Document document, String fileName, String type) {
        Element entry = findFileEntry(document, fileName);
        if (entry == null) {
            return null;
        }
        NodeList digestEntries = entry.getElementsByTagName("DIGEST_ENTRY");
        for (int i = 0; i < digestEntries.getLength(); i++) {
            Element digestEntry = (Element) digestEntries.item(i);
            String actualType = digestEntry.getElementsByTagName("DIGEST_TYPE")
                    .item(0).getTextContent().trim();
            if (type.equals(actualType)) {
                return digestEntry.getElementsByTagName("DIGEST_HEX")
                        .item(0).getTextContent().trim().toLowerCase();
            }
        }
        return null;
    }

    private static String lastFileEntryName(Document document) {
        String last = null;
        Node child = document.getDocumentElement().getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "FILE_ENTRY".equals(((Element) child).getTagName())) {
                Element entry = (Element) child;
                last = entry.getElementsByTagName("FILE_NAME")
                        .item(0).getTextContent().trim();
            }
            child = child.getNextSibling();
        }
        return last;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error)
                    throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void execute(String name, CheckedTest test) {
        try {
            test.run();
            passed++;
            System.out.println("[OK] " + name);
        } catch (Throwable error) {
            failed++;
            System.out.println("[FALHOU] " + name + ": " + error.getMessage());
            error.printStackTrace(System.out);
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(description + " - esperado: [" + expected
                    + "], obtido: [" + actual + "]");
        }
    }

    private static void assertNull(Object actual, String description) {
        if (actual != null) {
            throw new AssertionError(description + " - esperado null, obtido: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static void assertContains(String actual, String expectedPart, String description) {
        if (!actual.contains(expectedPart)) {
            throw new AssertionError(description + " - trecho nao encontrado: "
                    + expectedPart + " em [" + actual + "]");
        }
    }

    private interface CheckedTest {
        void run() throws Exception;
    }

    private static final class Invocation {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private Invocation(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static final class ProcessInvocation {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private ProcessInvocation(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
