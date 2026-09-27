
// Rafael Soares Estevão 2320470
// Dante Honorato Navaza 2321406


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

public final class DigestCalculator {
    private static final int BUFFER_SIZE = 8192;

    private DigestCalculator() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            usage();
            System.exit(1);
            return;
        }

        DigestType type;
        Path catalogPath;
        Path folder;
        try {
            type = DigestType.parse(args[0]);
            catalogPath = Paths.get(args[1]);
            folder = Paths.get(args[2]);
        } catch (Exception e) {
            System.err.println("Erro: " + message(e));
            usage();
            System.exit(1);
            return;
        }

        try {
            Catalog catalog = Catalog.load(catalogPath);
            List<Result> results = calculate(folder, type);
            classify(results, catalog, type);

            // Mostra o resultado de cada arquivo antes de atualizar o catalogo.
            for (Result result : results) {
                System.out.printf("%s %s %s (%s)%n",
                        result.name, type.externalName, result.hex, result.status);
            }

            // So adiciona ao catalogo os digests com status NOT FOUND.
            boolean changed = false;
            for (Result result : results) {
                if ("NOT FOUND".equals(result.status)) {
                    catalog.add(result.name, type, result.hex);
                    changed = true;
                }
            }
            if (changed) {
                catalog.save(catalogPath);
            }

        } catch (Exception e) {
            System.err.println("Erro: " + message(e));
            System.exit(2);
        }
    }

    private static void usage() {
        System.out.println("Java DigestCalculator <MD5|SHA1|SHA256|SHA512> "
                + "<arquivo_catalogo_xml> <pasta_arquivos>");
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static List<Result> calculate(Path folder, DigestType type)
            throws IOException, NoSuchAlgorithmException {
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        }
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                int result = a.getFileName().toString()
                        .compareToIgnoreCase(b.getFileName().toString());
                return result != 0 ? result
                        : a.getFileName().toString().compareTo(b.getFileName().toString());
            }
        });

        List<Result> results = new ArrayList<Result>();
        byte[] buffer = new byte[BUFFER_SIZE];
        for (Path file : files) {
            // Cria o MessageDigest seguindo o exemplo da aula.
            MessageDigest digest = MessageDigest.getInstance(type.jcaName);
            try (InputStream input = Files.newInputStream(file)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > 0) {
                        // O ultimo bloco pode ter menos bytes que o tamanho do buffer.
                        digest.update(buffer, 0, count);
                    }
                }
            }
            results.add(new Result(file.getFileName().toString(), hex(digest.digest())));
        }
        return results;
    }

    private static String hex(byte[] digest) {
        // Converte para hexadecimal usando o codigo do exemplo da aula.
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < digest.length; i++) {
            String hex = Integer.toHexString(0x0100 + (digest[i] & 0x00FF)).substring(1);
            buf.append((hex.length() < 2 ? "0" : "") + hex);
        }
        return buf.toString();
    }

    private static void classify(List<Result> results, Catalog catalog, DigestType type) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (Result result : results) {
            Integer count = counts.get(result.hex);
            counts.put(result.hex, count == null ? 1 : count + 1);
        }

        for (Result result : results) {
            if (counts.get(result.hex) > 1
                    || catalog.existsForOtherName(result.name, type, result.hex)) {
                result.status = "COLISION";
                continue;
            }
            String known = catalog.find(result.name, type);
            if (known == null) {
                result.status = "NOT FOUND";
            } else if (known.equalsIgnoreCase(result.hex)) {
                result.status = "OK";
            } else {
                result.status = "NOT OK";
            }
        }
    }

    private enum DigestType {
        MD5("MD5", "MD5", 32),
        SHA1("SHA1", "SHA-1", 40),
        SHA256("SHA256", "SHA-256", 64),
        SHA512("SHA512", "SHA-512", 128);

        private final String externalName;
        private final String jcaName;
        private final int hexLength;

        DigestType(String externalName, String jcaName, int hexLength) {
            this.externalName = externalName;
            this.jcaName = jcaName;
            this.hexLength = hexLength;
        }

        private static DigestType parse(String value) {
            for (DigestType type : values()) {
                if (type.externalName.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException(
                    "tipo de digest invalido. Use MD5, SHA1, SHA256 ou SHA512");
        }
    }

    private static final class Result {
        private final String name;
        private final String hex;
        private String status;

        private Result(String name, String hex) {
            this.name = name;
            this.hex = hex;
        }
    }

    private static final class Entry {
        private final Element node;
        private final Map<DigestType, String> digests =
                new EnumMap<DigestType, String>(DigestType.class);

        private Entry(Element node) {
            this.node = node;
        }
    }

    private static final class Catalog {
        private final Document document;
        private final Element root;
        private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

        private Catalog(Document document, Element root) {
            this.document = document;
            this.root = root;
        }

        private static Catalog load(Path path) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            byte[] content = Files.readAllBytes(path);
            Document document;
            if (isBlank(content)) {
                document = builder.newDocument();
                document.appendChild(document.createElement("CATALOG"));
            } else {
                document = builder.parse(new ByteArrayInputStream(content));
            }
            Element root = document.getDocumentElement();
            if (root == null || !"CATALOG".equals(root.getTagName())) {
                throw new IllegalArgumentException("a tag raiz deve ser CATALOG");
            }

            Catalog catalog = new Catalog(document, root);
            for (Element fileNode : children(root, "FILE_ENTRY")) {
                String name = one(fileNode, "FILE_NAME").getTextContent();
                if (name.length() == 0 || name.indexOf('/') >= 0
                        || name.indexOf(File.separatorChar) >= 0) {
                    throw new IllegalArgumentException("FILE_NAME invalido: " + name);
                }

                List<Element> digestNodes = children(fileNode, "DIGEST_ENTRY");
                if (digestNodes.isEmpty()) {
                    throw new IllegalArgumentException("FILE_ENTRY sem DIGEST_ENTRY: " + name);
                }

                Entry entry = new Entry(fileNode);
                catalog.entries.put(name, entry);
                for (Element digestNode : digestNodes) {
                    String typeName = one(digestNode, "DIGEST_TYPE").getTextContent();
                    String value = one(digestNode, "DIGEST_HEX")
                            .getTextContent().trim().toLowerCase(Locale.ROOT);
                    DigestType type = DigestType.parse(typeName);
                    if (value.length() != type.hexLength || !value.matches("[0-9a-f]+")) {
                        System.err.println("Aviso: DIGEST_HEX invalido para " + name + " ("
                                + type.externalName + ")");
                    }
                    entry.digests.put(type, value);
                }
            }
            return catalog;
        }

        private String find(String name, DigestType type) {
            Entry entry = entries.get(name);
            return entry == null ? null : entry.digests.get(type);
        }

        private boolean existsForOtherName(String name, DigestType type, String value) {
            for (Map.Entry<String, Entry> item : entries.entrySet()) {
                String other = item.getValue().digests.get(type);
                if (!item.getKey().equals(name) && value.equalsIgnoreCase(other)) {
                    return true;
                }
            }
            return false;
        }

        private void add(String name, DigestType type, String value) {
            Entry entry = entries.get(name);
            if (entry == null) {
                Element fileNode = document.createElement("FILE_ENTRY");
                Element nameNode = document.createElement("FILE_NAME");
                nameNode.setTextContent(name);
                fileNode.appendChild(nameNode);
                root.appendChild(fileNode);
                entry = new Entry(fileNode);
                entries.put(name, entry);
            }

            Element digestNode = document.createElement("DIGEST_ENTRY");
            Element typeNode = document.createElement("DIGEST_TYPE");
            Element hexNode = document.createElement("DIGEST_HEX");
            typeNode.setTextContent(type.externalName);
            hexNode.setTextContent(value);
            digestNode.appendChild(typeNode);
            digestNode.appendChild(hexNode);
            entry.node.appendChild(digestNode);
            entry.digests.put(type, value);
        }

        private void save(Path path) throws Exception {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            try (OutputStream output = Files.newOutputStream(path)) {
                transformer.transform(new DOMSource(document), new StreamResult(output));
            }
        }

        private static Element one(Element parent, String tag) {
            List<Element> matches = children(parent, tag);
            if (matches.size() != 1) {
                throw new IllegalArgumentException(
                        parent.getTagName() + " deve possuir exatamente um " + tag);
            }
            return matches.get(0);
        }

        private static List<Element> children(Element parent, String tag) {
            List<Element> result = new ArrayList<Element>();
            NodeList nodes = parent.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE
                        && tag.equals(((Element) node).getTagName())) {
                    result.add((Element) node);
                }
            }
            return result;
        }

        private static boolean isBlank(byte[] content) {
            for (char c : new String(content, StandardCharsets.UTF_8).toCharArray()) {
                if (c > ' ' && c != 0xFEFF) {
                    return false;
                }
            }
            return true;
        }
    }
}
