/*
 * Aluno 1: Rafael Soares Estevão
 * Matrícula 1: 2320470
 * Aluno 2: Dante Honorato Navaza
 * Matrícula 2: 2321406
 */

import java.io.IOException;
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

// Testes do programa, sem usar JUnit.
public final class DigestCalculatorTest {
    private static int passed;
    private static int failed;

    private DigestCalculatorTest() {
    }

    public static void main(String[] args) throws Exception {
        execute("fluxo completo e persistencia",
                DigestCalculatorTest::testCompleteWorkflowAndPersistence);
        execute("quatro algoritmos e arquivo vazio",
                DigestCalculatorTest::testAllAlgorithmsAndEmptyFile);
        execute("arquivo binario maior que o buffer",
                DigestCalculatorTest::testLargeBinaryFile);
        execute("validacao dos argumentos e caminhos",
                DigestCalculatorTest::testArgumentAndPathValidation);
        execute("lista vazia equivale a catalogo sem entradas",
                DigestCalculatorTest::testEmptyCatalogFiles);
        execute("avisos de hexadecimal e rejeicao de algoritmo invalido",
                DigestCalculatorTest::testCatalogValidationAndHexWarnings);
        execute("estrutura e valores do XML regravado",
                DigestCalculatorTest::testSavedXmlFormatting);
        execute("validacao estrutural do XML",
                DigestCalculatorTest::testXmlValidation);
        execute("main em processo Java separado",
                DigestCalculatorTest::testMainInSeparateProcess);
        execute("vetores conhecidos e exemplo da aula",
                DigestCalculatorTest::testKnownVectorsAndClassExample);
        execute("quatro digests no mesmo registro",
                DigestCalculatorTest::testAllDigestsInOneEntry);
        execute("colisao tem prioridade sobre todos os outros status",
                DigestCalculatorTest::testCollisionPrecedence);
        execute("nomes com caracteres XML, acentos e espacos",
                DigestCalculatorTest::testExactFileNames);
        execute("listagem antes da tentativa de gravacao",
                DigestCalculatorTest::testOutputBeforeCatalogUpdate);

        System.out.println();
        System.out.println("Resumo: " + passed + " teste(s) aprovado(s), "
                + failed + " teste(s) reprovado(s).");
        if (failed != 0) {
            throw new AssertionError("Existem testes reprovados.");
        }
    }

    private static void testCompleteWorkflowAndPersistence() throws Exception {
        try (TestFiles files = new TestFiles()) {

            writeUtf8(files.folder.resolve("ok.txt"), "conteudo-ok");
            writeUtf8(files.folder.resolve("not-ok.txt"), "conteudo-atualizado");
            writeUtf8(files.folder.resolve("existing-no-sha256.txt"),
                    "conteudo-existente");
            writeUtf8(files.folder.resolve("new.txt"), "conteudo-novo-unico");
            Files.write(files.folder.resolve("dup-a.bin"), new byte[] {0, 1, 2, 3, 4});
            Files.write(files.folder.resolve("dup-b.bin"), new byte[] {0, 1, 2, 3, 4});
            writeUtf8(files.folder.resolve("catalog-collision.bin"),
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
            writeUtf8(files.catalog, xml);

            Invocation first = invoke("SHA256", files.catalog.toString(), files.folder.toString());
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

            Document updated = parse(files.catalog);
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

            byte[] beforeSecondRun = Files.readAllBytes(files.catalog);
            Invocation second = invoke("SHA256", files.catalog.toString(), files.folder.toString());
            assertEquals(0, second.exitCode, "codigo de saida da segunda execucao");
            assertEquals("", second.stderr, "stderr da segunda execucao");
            String expectedSecond = expectedFirst.replace("(NOT FOUND)", "(OK)");
            assertEquals(expectedSecond, second.stdout,
                    "NOT FOUND deve se tornar OK na segunda execucao");
            assertTrue(Arrays.equals(beforeSecondRun, Files.readAllBytes(files.catalog)),
                    "catalogo nao deve ser regravado quando nao houver NOT FOUND");
        }
    }

    private static void testAllAlgorithmsAndEmptyFile() throws Exception {
        String[][] algorithms = {
            {"MD5", "MD5"},
            {"SHA1", "SHA-1"},
            {"SHA256", "SHA-256"},
            {"SHA512", "SHA-512"}
        };
        try (TestFiles files = new TestFiles()) {
            for (String[] algorithm : algorithms) {
                Path file = files.folder.resolve("empty.bin");
                Files.write(file, new byte[0]);
                writeUtf8(files.catalog, "<CATALOG/>\n");

                Invocation invocation = invoke(algorithm[0], files.catalog.toString(),
                        files.folder.toString());
                String expectedDigest = digest(algorithm[1], Files.readAllBytes(file));
                assertEquals(0, invocation.exitCode,
                        "codigo de saida para " + algorithm[0]);
                assertEquals(line("empty.bin", algorithm[0], expectedDigest, "NOT FOUND"),
                        invocation.stdout, "digest vazio para " + algorithm[0]);
                assertEquals(expectedDigest,
                        findDigest(parse(files.catalog), "empty.bin", algorithm[0]),
                        "persistencia para " + algorithm[0]);
            }
        }
    }

    private static void testLargeBinaryFile() throws Exception {
        try (TestFiles files = new TestFiles()) {
            byte[] content = new byte[BUFFER_SIZE_FOR_TEST * 3 + 137];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i * 31 + 7);
            }
            Files.write(files.folder.resolve("large.bin"), content);
            String expectedDigest = digest("SHA-512", content);
            writeUtf8(files.catalog, "<CATALOG>\n"
                    + fileEntry("large.bin", "SHA512", expectedDigest.toUpperCase())
                    + "</CATALOG>\n");

            Invocation invocation = invoke("SHA512", files.catalog.toString(), files.folder.toString());
            assertEquals(0, invocation.exitCode, "codigo para arquivo binario grande");
            assertEquals(line("large.bin", "SHA512", expectedDigest, "OK"),
                    invocation.stdout, "leitura em multiplos blocos");
            assertEquals("", invocation.stderr, "stderr para arquivo binario grande");
        }
    }

    private static final int BUFFER_SIZE_FOR_TEST = 8192;

    private static void testArgumentAndPathValidation() throws Exception {
        Invocation missingArguments = invoke();
        assertEquals(1, missingArguments.exitCode, "argumentos ausentes");
        assertContains(missingArguments.stdout, "Uso: java DigestCalculator",
                "orientacao de uso");
        assertEquals("", missingArguments.stderr, "argumentos ausentes nao sao erro interno");
        for (String[] args : new String[][] {
            {"MD5"}, {"MD5", "catalog.xml"}, {"MD5", "catalog.xml", "files", "extra"}
        }) {
            Invocation incomplete = invoke(args);
            assertEquals(1, incomplete.exitCode, "quantidade incorreta de argumentos");
            assertContains(incomplete.stdout, "Uso: java DigestCalculator", "orientacao de uso");
        }

        for (String invalid : new String[] {"CRC32", "sha256", " SHA256 ", "SHA-256"}) {
            Invocation invalidAlgorithm = invoke(invalid, "x", "y");
            assertEquals(1, invalidAlgorithm.exitCode, "algoritmo invalido: " + invalid);
            assertContains(invalidAlgorithm.stderr, "tipo de digest invalido",
                    "mensagem de algoritmo invalido");
        }

        try (TestFiles files = new TestFiles()) {
            writeUtf8(files.catalog, "<CATALOG/>\n");

            Invocation missingCatalog = invoke("SHA256",
                    files.root.resolve("missing.xml").toString(), files.folder.toString());
            assertEquals(2, missingCatalog.exitCode, "catalogo inexistente");
            assertContains(missingCatalog.stderr, "missing.xml",
                    "mensagem para catalogo inexistente");

            Invocation missingFolder = invoke("SHA256", files.catalog.toString(),
                    files.root.resolve("missing-folder").toString());
            assertEquals(2, missingFolder.exitCode, "pasta inexistente");
            assertContains(missingFolder.stderr, "missing-folder",
                    "mensagem para pasta inexistente");

            byte[] catalogBeforeEmptyRun = Files.readAllBytes(files.catalog);
            Invocation emptyFolder = invoke("SHA256", files.catalog.toString(), files.folder.toString());
            assertEquals(0, emptyFolder.exitCode, "pasta vazia e valida");
            assertEquals("", emptyFolder.stdout, "pasta vazia nao gera linhas");
            assertTrue(Arrays.equals(catalogBeforeEmptyRun, Files.readAllBytes(files.catalog)),
                    "pasta vazia nao regrava catalogo");

            Path current = java.nio.file.Paths.get("").toAbsolutePath();
            Invocation relativePaths = invoke("MD5", current.relativize(files.catalog).toString(),
                    current.relativize(files.folder).toString());
            assertEquals(0, relativePaths.exitCode, "caminhos relativos sao aceitos");
            assertEquals("", relativePaths.stderr, "stderr para caminhos relativos");

            Path catalogInsideFolder = files.folder.resolve("inside.xml");
            writeUtf8(catalogInsideFolder, "<CATALOG/>\n");
            writeUtf8(files.folder.resolve("data.txt"), "dados-da-pasta");
            Path subfolder = Files.createDirectory(files.folder.resolve("subpasta"));
            writeUtf8(subfolder.resolve("nao-processar.txt"), "dados-da-subpasta");
            String dataDigest = digest("SHA-256", bytes("dados-da-pasta"));
            String catalogDigest = digest("SHA-256", Files.readAllBytes(catalogInsideFolder));
            Invocation inside = invoke("SHA256", catalogInsideFolder.toString(),
                    files.folder.toString());
            assertEquals(0, inside.exitCode, "catalogo dentro da pasta deve ser aceito");
            assertEquals("", inside.stderr, "stderr com catalogo dentro da pasta");
            assertEquals(line("data.txt", "SHA256", dataDigest, "NOT FOUND")
                    + line("inside.xml", "SHA256", catalogDigest, "NOT FOUND"), inside.stdout,
                    "todos os arquivos da pasta, inclusive o catalogo, devem ser processados");
            Document insideCatalog = parse(catalogInsideFolder);
            assertEquals(dataDigest, findDigest(insideCatalog, "data.txt", "SHA256"),
                    "digest gravado no catalogo que esta dentro da pasta");
            assertEquals(catalogDigest, findDigest(insideCatalog, "inside.xml", "SHA256"),
                    "digest do catalogo deve corresponder aos bytes anteriores a atualizacao");
            String updatedCatalogDigest = digest("SHA-256", Files.readAllBytes(catalogInsideFolder));
            Invocation insideAgain = invoke("SHA256", catalogInsideFolder.toString(),
                    files.folder.toString());
            assertEquals(line("data.txt", "SHA256", dataDigest, "OK")
                    + line("inside.xml", "SHA256", updatedCatalogDigest, "NOT OK"),
                    insideAgain.stdout, "alteracao do proprio catalogo deve ser detectada");
        }
    }

    private static void testEmptyCatalogFiles() throws Exception {
        try (TestFiles files = new TestFiles()) {
            writeUtf8(files.folder.resolve("a.txt"), "conteudo-a");
            String expectedDigest = digest("MD5", bytes("conteudo-a"));
            byte[][] contents = {
                new byte[0],
                bytes(" \r\n\t\n"),
                new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}
            };
            for (int i = 0; i < contents.length; i++) {
                Files.write(files.catalog, contents[i]);
                Invocation invocation = invoke("MD5", files.catalog.toString(), files.folder.toString());
                assertEquals(0, invocation.exitCode, "codigo de saida da lista vazia " + i);
                assertEquals("", invocation.stderr, "stderr da lista vazia " + i);
                assertEquals(line("a.txt", "MD5", expectedDigest, "NOT FOUND"),
                        invocation.stdout, "saida com lista vazia " + i);
                assertEquals(expectedDigest, findDigest(parse(files.catalog), "a.txt", "MD5"),
                        "lista vazia deve receber o novo FILE_ENTRY " + i);
            }
        }
    }

    private static void testCatalogValidationAndHexWarnings() throws Exception {
        try (TestFiles files = new TestFiles()) {
            writeUtf8(files.folder.resolve("chars.txt"), "conteudo-chars");
            writeUtf8(files.folder.resolve("hex.txt"), "conteudo-hex");
            writeUtf8(files.folder.resolve("new.txt"), "conteudo-novo");
            String charsDigest = digest("SHA-256", bytes("conteudo-chars"));
            String hexDigest = digest("SHA-256", bytes("conteudo-hex"));
            String newDigest = digest("SHA-256", bytes("conteudo-novo"));
            String invalidCharacters = "z" + charsDigest.substring(1);
            writeUtf8(files.catalog, "<CATALOG>"
                    + fileEntry("chars.txt", "SHA256", invalidCharacters)
                    + fileEntry("hex.txt", "SHA256", "123abc") + "</CATALOG>");

            Invocation result = invoke("SHA256", files.catalog.toString(), files.folder.toString());
            assertEquals(0, result.exitCode, "hexadecimal invalido gera somente aviso");
            assertEquals(line("chars.txt", "SHA256", charsDigest, "NOT OK")
                    + line("hex.txt", "SHA256", hexDigest, "NOT OK")
                    + line("new.txt", "SHA256", newDigest, "NOT FOUND"),
                    result.stdout, "status com hexadecimal invalido");
            for (String name : new String[] {"chars.txt", "hex.txt"}) {
                assertContains(result.stderr, "DIGEST_HEX invalido para " + name,
                        "aviso de tamanho ou caracteres invalidos");
            }
            Document updated = parse(files.catalog);
            assertEquals(invalidCharacters, findDigest(updated, "chars.txt", "SHA256"),
                    "hexadecimal com caracteres invalidos preservado");
            assertEquals("123abc", findDigest(updated, "hex.txt", "SHA256"),
                    "hexadecimal curto preservado");
            assertEquals(newDigest, findDigest(updated, "new.txt", "SHA256"),
                    "NOT FOUND acrescentado apesar dos avisos");

            for (String invalid : new String[] {"CRC32", "sha256", " SHA256 ", "SHA-256"}) {
                String xml = "<CATALOG>" + fileEntry("hex.txt", invalid, hexDigest) + "</CATALOG>";
                writeUtf8(files.catalog, xml);
                Invocation rejected = invoke("SHA256", files.catalog.toString(), files.folder.toString());
                assertEquals(2, rejected.exitCode, "DIGEST_TYPE invalido deve encerrar a execucao");
                assertContains(rejected.stderr, "tipo de digest invalido", "erro de DIGEST_TYPE");
                assertEquals("", rejected.stdout, "catalogo invalido nao gera listagem");
                assertEquals(xml, new String(Files.readAllBytes(files.catalog), StandardCharsets.UTF_8),
                        "catalogo invalido permanece inalterado");
            }
        }
    }

    private static void testSavedXmlFormatting() throws Exception {
        try (TestFiles files = new TestFiles()) {
            writeUtf8(files.folder.resolve("Arquivo1.dat"), "conteudo-1");
            writeUtf8(files.folder.resolve("Arquivo2.dat"), "conteudo-2");
            String sha1 = digest("SHA-1", bytes("conteudo-1"));
            // Usa tabs e espaco antes do digest, como no exemplo do enunciado.
            writeUtf8(files.catalog, "<CATALOG>\n"
                    + "\t<FILE_ENTRY>\n"
                    + "\t\t<FILE_NAME>Arquivo1.dat</FILE_NAME>\n"
                    + "\t\t<DIGEST_ENTRY>\n"
                    + "\t\t\t<DIGEST_TYPE>SHA1</DIGEST_TYPE>\n"
                    + "\t\t\t<DIGEST_HEX> " + sha1 + "</DIGEST_HEX>\n"
                    + "\t\t</DIGEST_ENTRY>\n"
                    + "\t</FILE_ENTRY>\n"
                    + "</CATALOG>\n");

            for (String type : new String[] {"SHA256", "MD5"}) {
                Invocation invocation = invoke(type, files.catalog.toString(), files.folder.toString());
                assertEquals(0, invocation.exitCode, "codigo de saida com " + type);
                assertEquals("", invocation.stderr, "stderr com " + type);
            }

            Document updated = parse(files.catalog);
            assertEquals("CATALOG", updated.getDocumentElement().getTagName(), "raiz mantida");
            assertEquals("UTF-8", updated.getXmlEncoding(), "codificacao do XML");
            assertEquals(2, updated.getElementsByTagName("FILE_ENTRY").getLength(), "dois arquivos");
            assertEquals(5, updated.getElementsByTagName("DIGEST_ENTRY").getLength(), "cinco digests");
            assertEquals(sha1, findDigest(updated, "Arquivo1.dat", "SHA1"), "SHA1 original preservado");
            assertEquals("Arquivo2.dat", lastFileEntryName(updated), "novo arquivo no final");
            for (int i = 1; i <= 2; i++) {
                String name = "Arquivo" + i + ".dat";
                byte[] content = bytes("conteudo-" + i);
                assertEquals(digest("SHA-256", content), findDigest(updated, name, "SHA256"),
                        "SHA256 acrescentado ao arquivo correto");
                assertEquals(digest("MD5", content), findDigest(updated, name, "MD5"),
                        "MD5 acrescentado ao arquivo correto");
            }
            Invocation again = invoke("SHA256", files.catalog.toString(), files.folder.toString());
            assertEquals(0, again.exitCode, "XML regravado pode ser relido");
            assertEquals(line("Arquivo1.dat", "SHA256", digest("SHA-256", bytes("conteudo-1")), "OK")
                    + line("Arquivo2.dat", "SHA256", digest("SHA-256", bytes("conteudo-2")), "OK"),
                    again.stdout, "digests persistem apos regravacoes");
        }
    }

    private static void testXmlValidation() throws Exception {
        try (TestFiles files = new TestFiles()) {
            writeUtf8(files.folder.resolve("file.txt"), "conteudo");

            Path wrongRoot = files.root.resolve("wrong-root.xml");
            writeUtf8(wrongRoot, "<WRONG/>\n");
            byte[] wrongRootBefore = Files.readAllBytes(wrongRoot);
            Invocation wrongRootResult = invoke("SHA256", wrongRoot.toString(),
                    files.folder.toString());
            assertEquals(2, wrongRootResult.exitCode, "raiz XML invalida");
            assertContains(wrongRootResult.stderr, "tag raiz deve ser CATALOG",
                    "mensagem de raiz invalida");
            assertTrue(Arrays.equals(wrongRootBefore, Files.readAllBytes(wrongRoot)),
                    "XML invalido deve permanecer inalterado");

            Path missingHex = files.root.resolve("missing-hex.xml");
            writeUtf8(missingHex, "<CATALOG><FILE_ENTRY><FILE_NAME>file.txt</FILE_NAME>"
                    + "<DIGEST_ENTRY><DIGEST_TYPE>SHA256</DIGEST_TYPE></DIGEST_ENTRY>"
                    + "</FILE_ENTRY></CATALOG>");
            byte[] missingHexBefore = Files.readAllBytes(missingHex);
            Invocation missingHexResult = invoke("SHA256", missingHex.toString(),
                    files.folder.toString());
            assertEquals(2, missingHexResult.exitCode, "DIGEST_ENTRY sem DIGEST_HEX");
            assertContains(missingHexResult.stderr, "exatamente um DIGEST_HEX",
                    "mensagem de DIGEST_HEX ausente");
            assertTrue(Arrays.equals(missingHexBefore, Files.readAllBytes(missingHex)),
                    "catalogo estruturalmente invalido deve permanecer inalterado");

            Path malformed = files.root.resolve("malformed.xml");
            writeUtf8(malformed, "<CATALOG><FILE_ENTRY></CATALOG>");
            byte[] malformedBefore = Files.readAllBytes(malformed);
            Invocation malformedResult = invoke("SHA256", malformed.toString(), files.folder.toString());
            assertEquals(2, malformedResult.exitCode, "XML malformado deve ser rejeitado");
            assertContains(malformedResult.stderr, "Erro:", "erro de sintaxe XML comunicado");
            assertTrue(Arrays.equals(malformedBefore, Files.readAllBytes(malformed)),
                    "XML malformado deve permanecer inalterado");
        }
    }

    private static void testMainInSeparateProcess() throws Exception {
        Invocation withoutArguments = invoke();
        assertEquals(1, withoutArguments.exitCode,
                "codigo real do processo sem argumentos");
        assertContains(withoutArguments.stdout, "Uso: java DigestCalculator",
                "uso impresso pelo main real");
        assertEquals("", withoutArguments.stderr,
                "stderr do processo real sem argumentos");

        try (TestFiles files = new TestFiles()) {
            writeUtf8(files.folder.resolve("main.txt"), "processo-separado");
            writeUtf8(files.catalog, "<CATALOG/>\n");

            String expectedDigest = digest("SHA-1", bytes("processo-separado"));
            Invocation valid = invoke("SHA1", files.catalog.toString(),
                    files.folder.toString());
            assertEquals(0, valid.exitCode, "codigo real do processo valido");
            assertEquals(line("main.txt", "SHA1", expectedDigest, "NOT FOUND"),
                    valid.stdout, "stdout do processo Java real");
            assertEquals("", valid.stderr, "stderr do processo Java real");
            assertEquals(expectedDigest, findDigest(parse(files.catalog), "main.txt", "SHA1"),
                    "processo real deve persistir o digest");
        }
    }

    private static void testKnownVectorsAndClassExample() throws Exception {
        // Digests conhecidos para conferir se o calculo esta certo.
        String[][] vectors = {
            {"MD5", "abc", "900150983cd24fb0d6963f7d28e17f72"},
            {"SHA1", "abc", "a9993e364706816aba3e25717850c26c9cd0d89d"},
            {"SHA256", "abc", "ba7816bf8f01cfea414140de5dae2223"
                    + "b00361a396177a9cb410ff61f20015ad"},
            {"SHA512", "abc", "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20"
                    + "a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454"
                    + "d4423643ce80e2a9ac94fa54ca49f"},
            {"MD5", "", "d41d8cd98f00b204e9800998ecf8427e"},
            {"SHA1", "", "da39a3ee5e6b4b0d3255bfef95601890afd80709"},
            {"SHA256", "", "e3b0c44298fc1c149afbf4c8996fb924"
                    + "27ae41e4649b934ca495991b7852b855"},
            {"SHA512", "", "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc"
                    + "83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f"
                    + "63b931bd47417a81a538327af927da3e"},
            // Mesmo texto e digest do exemplo de Java Security, na pagina 19.
            {"SHA256", "Este eh um teste!", "bae8f0ffa90cb82565e8f745e21a881a"
                    + "1649af5432d19f0b8abc90aa652165ab"}
        };
        try (TestFiles files = new TestFiles()) {
            for (String[] vector : vectors) {
                writeUtf8(files.catalog, "<CATALOG/>");
                writeUtf8(files.folder.resolve("entrada.txt"), vector[1]);
                Invocation result = invoke(vector[0], files.catalog.toString(), files.folder.toString());
                assertEquals(0, result.exitCode, "vetor conhecido " + vector[0]);
                assertEquals("", result.stderr, "stderr do vetor conhecido");
                assertEquals(line("entrada.txt", vector[0], vector[2], "NOT FOUND"),
                        result.stdout, "vetor conhecido para [" + vector[1] + "]");
                assertEquals(vector[2], findDigest(parse(files.catalog), "entrada.txt", vector[0]),
                        "persistencia do vetor conhecido");
            }
        }
    }

    private static void testAllDigestsInOneEntry() throws Exception {
        try (TestFiles files = new TestFiles()) {
            writeUtf8(files.folder.resolve("arquivo.txt"), "quatro algoritmos");
            writeUtf8(files.catalog, "<CATALOG/>");
            String[][] algorithms = {
                {"SHA512", "SHA-512"}, {"MD5", "MD5"},
                {"SHA256", "SHA-256"}, {"SHA1", "SHA-1"}
            };
            for (String[] algorithm : algorithms) {
                Invocation first = invoke(algorithm[0], files.catalog.toString(), files.folder.toString());
                assertEquals(0, first.exitCode, "inclusao de " + algorithm[0]);
                assertEquals(line("arquivo.txt", algorithm[0],
                        digest(algorithm[1], bytes("quatro algoritmos")), "NOT FOUND"),
                        first.stdout, "algoritmo ainda nao catalogado");
            }
            Document updated = parse(files.catalog);
            assertEquals(1, updated.getElementsByTagName("FILE_ENTRY").getLength(),
                    "um unico FILE_ENTRY para os quatro algoritmos");
            assertEquals(4, updated.getElementsByTagName("DIGEST_ENTRY").getLength(),
                    "quatro DIGEST_ENTRY no mesmo arquivo");
            byte[] before = Files.readAllBytes(files.catalog);
            for (String[] algorithm : algorithms) {
                Invocation again = invoke(algorithm[0], files.catalog.toString(), files.folder.toString());
                assertEquals(0, again.exitCode, "consulta de " + algorithm[0]);
                assertEquals(line("arquivo.txt", algorithm[0],
                        digest(algorithm[1], bytes("quatro algoritmos")), "OK"),
                        again.stdout, "algoritmo existente em qualquer ordem");
            }
            assertTrue(Arrays.equals(before, Files.readAllBytes(files.catalog)),
                    "consulta dos quatro digests nao deve regravar o XML");
        }
    }

    private static void testCollisionPrecedence() throws Exception {
        try (TestFiles files = new TestFiles()) {
            String value = digest("MD5", bytes("conteudo-igual"));
            String different = digest("MD5", bytes("conteudo-antigo"));
            String[] names = {"a-ok.txt", "b-not-ok.txt", "c-not-found.txt"};
            for (String name : names) {
                writeUtf8(files.folder.resolve(name), "conteudo-igual");
            }
            String original = "<CATALOG>" + fileEntry(names[0], "MD5", value)
                    + fileEntry(names[1], "MD5", different) + "</CATALOG>";
            writeUtf8(files.catalog, original);
            Invocation inFolder = invoke("MD5", files.catalog.toString(), files.folder.toString());
            assertEquals(0, inFolder.exitCode, "colisoes na pasta");
            String expected = "";
            for (String name : names) {
                expected += line(name, "MD5", value, "COLISION");
            }
            assertEquals(expected, inFolder.stdout, "colisao prevalece sobre OK/NOT OK/NOT FOUND");
            assertEquals(original, new String(Files.readAllBytes(files.catalog), StandardCharsets.UTF_8),
                    "somente colisoes nao alteram o catalogo");
            for (String name : names) {
                Files.delete(files.folder.resolve(name));
            }

            // Agora verifica a colisao com um arquivo que so esta no catalogo.
            original = "<CATALOG>" + fileEntry(names[0], "MD5", value)
                    + fileEntry(names[1], "MD5", different)
                    + fileEntry("ausente.txt", "MD5", value.toUpperCase()) + "</CATALOG>";
            writeUtf8(files.catalog, original);
            for (String name : names) {
                writeUtf8(files.folder.resolve(name), "conteudo-igual");
                Invocation result = invoke("MD5", files.catalog.toString(), files.folder.toString());
                assertEquals(0, result.exitCode, "colisao com nome do catalogo");
                assertEquals(line(name, "MD5", value, "COLISION"), result.stdout,
                        "colisao catalogada prevalece para " + name);
                assertEquals(original,
                        new String(Files.readAllBytes(files.catalog), StandardCharsets.UTF_8),
                        "colisoes catalogadas nao alteram o XML");
                Files.delete(files.folder.resolve(name));
            }
        }
    }

    private static void testExactFileNames() throws Exception {
        try (TestFiles files = new TestFiles()) {
            List<String> names = new ArrayList<String>();
            names.add(" dados.txt");
            names.add("a\u00e7\u00e3o & dados.txt");
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                names.add("fim.txt ");
                names.add("   ");
                names.add("<arquivo>.txt");
                names.add("barra\\invertida.txt");
            }
            for (String name : names) {
                writeUtf8(files.folder.resolve(name), "conteudo de " + name);
            }
            writeUtf8(files.catalog, "<CATALOG/>");
            Invocation first = invoke("SHA256", files.catalog.toString(), files.folder.toString());
            assertEquals(0, first.exitCode, "primeira execucao com nomes especiais");
            Invocation second = invoke("SHA256", files.catalog.toString(), files.folder.toString());
            assertEquals(0, second.exitCode, "segunda execucao com nomes especiais");
            assertEquals(first.stdout.replace("(NOT FOUND)", "(OK)"), second.stdout,
                    "FILE_NAME deve preservar o nome exato, incluindo espacos");
            assertEquals(names.size(), parse(files.catalog).getElementsByTagName("FILE_ENTRY").getLength(),
                    "nomes especiais nao devem gerar registros duplicados");

            // Adiciona outro algoritmo para conferir se os nomes continuam iguais.
            Invocation anotherType = invoke("MD5", files.catalog.toString(), files.folder.toString());
            assertEquals(0, anotherType.exitCode, "inclusao de outro algoritmo");
            Invocation third = invoke("SHA256", files.catalog.toString(), files.folder.toString());
            assertEquals(0, third.exitCode, "terceira execucao com nomes especiais");
            assertEquals(second.stdout, third.stdout, "reindentacao nao pode apagar o nome");
        }
    }

    private static void testOutputBeforeCatalogUpdate() throws Exception {
        try (TestFiles files = new TestFiles()) {
            writeUtf8(files.catalog, "<CATALOG/>");
            byte[] before = Files.readAllBytes(files.catalog);
            writeUtf8(files.folder.resolve("a.txt"), "a");
            writeUtf8(files.folder.resolve("b.txt"), "b");
            try {
                assertTrue(files.catalog.toFile().setWritable(false, false), "catalogo somente leitura");
                assertTrue(!Files.isWritable(files.catalog), "teste requer usuario sem privilegio de escrita");
                Invocation result = invoke("MD5", files.catalog.toString(), files.folder.toString());
                assertEquals(2, result.exitCode, "falha de gravacao deve encerrar com erro");
                assertEquals(line("a.txt", "MD5", digest("MD5", bytes("a")), "NOT FOUND")
                        + line("b.txt", "MD5", digest("MD5", bytes("b")), "NOT FOUND"),
                        result.stdout, "lista completa impressa antes da tentativa de gravacao");
                assertContains(result.stderr, "Erro:", "falha de escrita comunicada");
                assertTrue(Arrays.equals(before, Files.readAllBytes(files.catalog)),
                        "catalogo somente leitura permanece inalterado");
            } finally {
                files.catalog.toFile().setWritable(true);
            }
        }
    }

    private static Invocation invoke(String... args) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"),
                "bin", windows ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable);
        command.add("-Dfile.encoding=UTF-8");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("DigestCalculator");
        command.addAll(Arrays.asList(args));

        Path capture = Files.createTempDirectory("digest-calculator-output-");
        try {
            Path stdout = capture.resolve("stdout.txt");
            Path stderr = capture.resolve("stderr.txt");
            Process process = new ProcessBuilder(command)
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
            int exitCode = process.waitFor();
            return new Invocation(exitCode,
                    new String(Files.readAllBytes(stdout), StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(stderr), StandardCharsets.UTF_8));
        } finally {
            deleteRecursively(capture);
        }
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
                    && fileName.equals(names.item(0).getTextContent())) {
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

    // Cria os arquivos temporarios de cada teste e apaga tudo no final.
    private static final class TestFiles implements AutoCloseable {
        private final Path root = Files.createTempDirectory("digest-calculator-test-");
        private final Path folder = Files.createDirectory(root.resolve("pasta com espacos"));
        private final Path catalog = root.resolve("catalogo com espacos.xml");

        private TestFiles() throws IOException {
        }

        @Override
        public void close() throws IOException {
            deleteRecursively(root);
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
}
