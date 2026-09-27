# DigestCalculator

Implementação do Trabalho 2 da disciplina INF1416 — Segurança da Informação.
O programa usa a Java Cryptography Architecture (JCA) para calcular o digest do
conteúdo de todos os arquivos regulares encontrados diretamente em uma pasta,
compará-los com um catálogo XML e registrar digests ainda desconhecidos.

Não há interface gráfica nem dependências externas.

## Relação com os materiais das aulas

A referência de implementação é `MessageDigestExample.java`, do arquivo
`enunciado/Fontes-2024.1-P1.zip`, reproduzido nas páginas 16–19 de
`enunciado/SI-INF1416-Vol5-Java Security.pdf`:

- criação com `MessageDigest.getInstance`, usando os nomes JCA `MD5`, `SHA-1`,
  `SHA-256` e `SHA-512`;
- sequência `update` seguida de `digest`, como no exemplo;
- conversão hexadecimal com o mesmo `StringBuffer`, máscara `0x00FF`,
  `Integer.toHexString` e `substring(1)` da página 18 e do ZIP;
- validação da quantidade de argumentos e encerramento pelo `main`.

O exemplo da aula calcula o digest de um texto passado na linha de comando.
O trabalho exige o **conteúdo binário de arquivos**. Por isso, a adaptação usa
`update(buffer, 0, count)`: somente os bytes efetivamente lidos entram no digest,
incluindo o último bloco parcial (observações 3 e 4 do trabalho).
As impressões de provider e tamanho existentes no exemplo não fazem parte da
saída exigida pelo trabalho; a saída aqui contém somente as linhas de resultado.

O XML e a classificação dos arquivos são implementados especificamente para o
enunciado, com APIs do próprio Java 8. Os PDFs e ZIPs fornecidos não contêm um
DigestCalculator completo nem uma implementação de catálogo XML para copiar.

Os demais materiais foram conferidos quanto à relação com este trabalho:

| Material | Aplicação ao DigestCalculator |
| --- | --- |
| `Trabalho2.pdf` e `Trabalho2 (1).pdf` | Arquivos idênticos; definem CLI, XML, status, atualização e Java 8. |
| Vol. 3 — Criptografia, pp. 102–106 | Resumo de mensagem, conteúdo, integridade e tamanhos de MD5/SHA. |
| Vol. 5 — Java Security, pp. 16–19; ZIP P1 | Exemplo JCA efetivamente seguido pelo cálculo e pela conversão hexadecimal. |
| Demais exemplos do ZIP P1 | Cifras, MAC, assinaturas e providers; não são etapas pedidas no trabalho. |
| ZIP P2 | Servidor HTTPS e applets de providers; não contém cálculo de digest de arquivos. |
| Vols. 1, 2 e 4; prática OpenSSL/CA | Contexto de segurança, normas, comunicação e certificados; não definem outro formato de implementação para este programa. |

O JAR Bouncy Castle não é necessário: assim como o exemplo de digest da aula,
o programa usa os providers já disponíveis no JDK.

## Arquivos do projeto

```text
DigestCalculator.java              Programa que deve ser entregue
tests/DigestCalculatorTest.java    Testes automatizados sem JUnit
enunciado/Trabalho2.pdf            Enunciado original
README.md                          Documentação
```

Os arquivos `.class` gerados pela compilação não devem ser submetidos.

## Requisitos

- JDK 8 para a validação final exigida pelo enunciado;
- PowerShell para os comandos Windows, ou um shell para os comandos macOS/Linux.

O código utiliza exclusivamente APIs disponíveis no Java 8.

## Compilação

No PowerShell, a partir da raiz deste projeto:

```powershell
New-Item -ItemType Directory -Force .\build | Out-Null
javac -encoding UTF-8 -source 1.8 -target 1.8 -d .\build .\DigestCalculator.java
```

Se a compilação for feita temporariamente com JDK 9 ou posterior, use
`--release 8` para impedir o uso acidental de APIs posteriores ao Java 8:

```powershell
javac --release 8 -encoding UTF-8 -d .\build .\DigestCalculator.java
```

Essa segunda forma verifica compatibilidade de API e bytecode, mas a validação
final ainda deve ser executada com um JDK 8 real.

No macOS/Linux, com JDK 8 selecionado no `PATH`:

```sh
mkdir -p build
javac -encoding UTF-8 -source 1.8 -target 1.8 -d build DigestCalculator.java
java -cp build DigestCalculator SHA256 "dados/catalogo.xml" "dados/arquivos"
```

## Execução

O programa recebe exatamente três argumentos:

```text
java DigestCalculator <MD5|SHA1|SHA256|SHA512> <arquivo_catalogo_xml> <pasta_arquivos>
```

O algoritmo deve ser escrito exatamente como acima, em maiúsculas e sem
espaços adicionais. A mesma regra vale para `DIGEST_TYPE` no catálogo.

Com as classes compiladas em `build`:

```powershell
java -cp .\build DigestCalculator SHA256 ".\dados\catalogo.xml" ".\dados\arquivos"
```

Use aspas nos caminhos que contenham espaços. O catálogo XML pode ficar dentro
da pasta processada: nesse caso ele também é calculado e listado, pois o item 1
do enunciado pede todos os arquivos da pasta, sem prever exceção para o catálogo.
Seu digest corresponde ao conteúdo anterior à atualização do XML. Se a execução
modificar o catálogo, seu próprio registro poderá ficar `NOT OK` na próxima
execução. Para verificar apenas os arquivos de dados, coloque o catálogo fora
da pasta de entrada, como nos exemplos de comandos.

Quando os argumentos são omitidos ou estão em quantidade incorreta, o programa
imprime:

```text
Uso: java DigestCalculator <MD5|SHA1|SHA256|SHA512> <arquivo_catalogo_xml> <pasta_arquivos>
```

## Formato do catálogo

Um arquivo vazio (0 bytes, só com espaços e quebras de linha, ou só com o BOM
do UTF-8) é aceito como catálogo sem entradas. Um catálogo vazio explícito
também pode ser criado assim:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<CATALOG/>
```

Exemplo com um registro SHA256 para um arquivo vazio:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<CATALOG>
  <FILE_ENTRY>
    <FILE_NAME>empty.bin</FILE_NAME>
    <DIGEST_ENTRY>
      <DIGEST_TYPE>SHA256</DIGEST_TYPE>
      <DIGEST_HEX>e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855</DIGEST_HEX>
    </DIGEST_ENTRY>
  </FILE_ENTRY>
</CATALOG>
```

As seguintes verificações de estrutura interrompem a execução sem alterar o arquivo:

- a raiz deve ser `CATALOG`;
- cada `FILE_ENTRY` deve ter exatamente um `FILE_NAME` e um ou mais
  `DIGEST_ENTRY`;
- cada `DIGEST_ENTRY` deve ter um `DIGEST_TYPE` e um `DIGEST_HEX`;
- `FILE_NAME` deve conter somente o nome, sem caminho.

Um `DIGEST_TYPE` desconhecido interrompe a execução com erro, preservando o
catálogo. Não há recuperação especial de tipos inválidos ou reunião de
registros duplicados. Use um `FILE_ENTRY` por arquivo e um `DIGEST_ENTRY` por
algoritmo, como no modelo do trabalho. Se houver repetições, a atribuição no
mapa mantém a última entrada lida; o XML não é corrigido automaticamente.

Os avisos de `DIGEST_HEX` com tamanho errado ou caracteres não hexadecimais
foram mantidos. O processamento continua, o valor catalogado não é substituído
e o arquivo fica `NOT OK`, salvo se houver uma colisão, que tem prioridade.

O leitor XML usa a configuração padrão do JDK, sem as configurações adicionais
de DTD, entidades externas, XInclude ou tratador personalizado de erros.

## Cálculo e classificação

Os arquivos são lidos em blocos binários de 8192 bytes por meio de
`MessageDigest.update(byte[], int, int)`. Portanto, o digest corresponde ao
conteúdo do arquivo, e não ao seu nome, e arquivos grandes não precisam ser
carregados inteiros na memória.

A conversão para hexadecimal segue o código da aula e mantém dois caracteres
por byte, incluindo zeros à esquerda: 32 para MD5, 40 para SHA1, 64 para SHA256
e 128 para SHA512.

A classificação segue esta precedência:

1. `COLISION`: o digest calculado coincide com o digest de outro nome no
   catálogo ou com outro arquivo da pasta;
2. `OK`: o catálogo contém o mesmo nome, algoritmo e digest;
3. `NOT OK`: o catálogo contém o mesmo nome e algoritmo, mas outro digest;
4. `NOT FOUND`: não existe digest catalogado para aquela combinação de nome e
   algoritmo.

A grafia `COLISION`, com um único `L`, reproduz exatamente o enunciado.
O aviso do EAD resume os nomes como `NOTOK` e `NOTFOUND`; o PDF do trabalho
define a saída como `NOT OK` e `NOT FOUND`, com espaços. O programa segue esse
formato explícito do PDF.
Comparações de colisão usam somente digests do algoritmo solicitado. Digests
hexadecimais do catálogo aceitam letras maiúsculas ou minúsculas e espaços nas
extremidades.

A saída possui uma linha por arquivo, ordenada por nome:

```text
Nome_Arq Tipo_Digest Digest_Hex (STATUS)
```

Somente registros `NOT FOUND` são acrescentados ao catálogo:

- se o nome já existe, é adicionado um novo `DIGEST_ENTRY`;
- se o nome é novo, um `FILE_ENTRY` é acrescentado ao final;
- `OK`, `NOT OK` e `COLISION` não são adicionados nem substituídos.

O XML somente é regravado depois que todos os arquivos foram calculados e
classificados com sucesso e a lista foi impressa, seguindo a ordem dos itens
1–4 do enunciado. Se não houver `NOT FOUND`, nenhuma escrita é feita.
Ao regravar, o `Transformer` do JDK gera a declaração XML e a indentação,
com codificação UTF-8. Não há limpeza manual da indentação anterior nem
quantidade fixa de espaços. A estrutura e os valores do catálogo são mantidos;
a apresentação pode variar entre versões do JDK.

## Decisões de escopo

- somente arquivos regulares diretamente na pasta são processados;
- subpastas não são percorridas;
- todos os arquivos regulares, inclusive ocultos e o catálogo, são processados;
- nomes de arquivo são comparados exatamente, inclusive maiúsculas e
  minúsculas, acentos e espaços nas extremidades;
- a ordem da saída é determinística;
- XML malformado, raiz incorreta e ausência das tags obrigatórias são rejeitados
  sem alteração;
- se não houver nenhum `NOT FOUND`, o catálogo não é regravado.

## Testes automatizados

Os testes não exigem JUnit. Para compilá-los com JDK 8:

```powershell
New-Item -ItemType Directory -Force .\build | Out-Null
javac -encoding UTF-8 -source 1.8 -target 1.8 -d .\build .\DigestCalculator.java .\tests\DigestCalculatorTest.java
java -cp .\build DigestCalculatorTest
```

Todos os cenários executam `DigestCalculator.main` em outro processo Java e
conferem saída padrão, saída de erro, código de encerramento e catálogo. A
aplicação não contém um método separado apenas para facilitar os testes.

Em JDK 9 ou posterior:

```powershell
javac --release 8 -encoding UTF-8 -Xlint:all -d .\build .\DigestCalculator.java .\tests\DigestCalculatorTest.java
java -cp .\build DigestCalculatorTest
```

No macOS/Linux, com JDK 8 no `PATH`:

```sh
mkdir -p build
javac -encoding UTF-8 -Xlint:all -d build DigestCalculator.java tests/DigestCalculatorTest.java
java -cp build DigestCalculatorTest
```

Os cenários verificam:

- `OK`, `NOT OK`, `NOT FOUND` e `COLISION` na mesma execução;
- colisão entre arquivos da pasta e colisão com outro nome no catálogo;
- inclusão em nome existente e criação de novo `FILE_ENTRY` ao final;
- transformação de `NOT FOUND` em `OK` na segunda execução;
- ausência de regravação quando o catálogo não muda;
- MD5, SHA1, SHA256 e SHA512, inclusive sobre arquivo vazio;
- arquivo binário maior que o buffer de leitura;
- argumentos, caminhos relativos, caminhos inexistentes e algoritmo inválido;
- pasta vazia e catálogo dentro da pasta processada;
- lista vazia, só com espaços ou só com BOM;
- avisos de `DIGEST_HEX` inválido, preservando o valor original;
- rejeição de `DIGEST_TYPE` desconhecido, em minúsculas ou com espaços;
- estrutura e valores do XML regravado a partir do layout do enunciado;
- rejeição de XML malformado ou com estrutura obrigatória ausente;
- execução do `main` em outro processo Java, incluindo seus códigos de saída;
- vetores fixos para os quatro algoritmos (arquivo vazio e `abc`), sem calcular
  a resposta esperada com a mesma API do programa;
- reprodução do SHA256 de `Este eh um teste!`, mostrado na página 19 da aula;
- os quatro algoritmos no mesmo `FILE_ENTRY`, acrescentados em qualquer ordem;
- prioridade de `COLISION` sobre `OK`, `NOT OK` e `NOT FOUND`;
- preservação de nomes com espaços nas extremidades, acentos e caracteres XML;
- impressão da lista completa antes da tentativa de gravação, inclusive
  quando o catálogo está sem permissão de escrita.

Na revisão de 27/09/2026, os testes foram executados no Amazon Corretto JDK
`11.0.24`, compilando com `--release 8`, e no **Azul Zulu OpenJDK `1.8.0_504`**,
compilando com o próprio `javac 1.8.0_504`. Ambos passaram sem avisos de
compilação com `-Xlint:all`:

```text
[OK] fluxo completo e persistencia
[OK] quatro algoritmos e arquivo vazio
[OK] arquivo binario maior que o buffer
[OK] validacao dos argumentos e caminhos
[OK] lista vazia equivale a catalogo sem entradas
[OK] avisos de hexadecimal e rejeicao de algoritmo invalido
[OK] estrutura e valores do XML regravado
[OK] validacao estrutural do XML
[OK] main em processo Java separado
[OK] vetores conhecidos e exemplo da aula
[OK] quatro digests no mesmo registro
[OK] colisao tem prioridade sobre todos os outros status
[OK] nomes com caracteres XML, acentos e espacos
[OK] listagem antes da tentativa de gravacao

Resumo: 14 teste(s) aprovado(s), 0 teste(s) reprovado(s).
```

Após a simplificação, foram conferidos **152 resultados** contra `hashlib` do
Python e contra a versão anterior do programa. Foram preservados os digests,
status, ordem da listagem e registros lógicos do XML. Os casos abrangem os
quatro algoritmos, os quatro status, colisões na pasta e no catálogo, arquivos
de 0, 1, 255, 256, 8191, 8192, 8193, 16384, 24713 e 1048577 bytes e um arquivo
oculto, com duas execuções de cada cenário para verificar a persistência.

`DigestCalculator.java` também foi compilado isoladamente no Java 8, sem o
arquivo de testes. O exemplo original do professor foi compilado e executado
na revisão e reproduziu o digest da aula; esse valor permanece coberto por um
teste fixo. Um catálogo sem permissão de escrita gerou código 2, manteve a
listagem e preservou o conteúdo original.

O enunciado menciona especificamente **Sun JDK 1.8**. Esta revisão comprova
compilação e execução em Java 8 usando a distribuição Azul OpenJDK; não foi
executada uma distribuição Sun/Oracle. Nenhum código depende de APIs do Azul.

## Conferência dos requisitos do trabalho

| Requisito | Implementação / verificação |
| --- | --- |
| JCA, sem interface gráfica | `java.security.MessageDigest`; somente entrada por argumentos e saída de console. |
| Nome e três argumentos | Classe pública `DigestCalculator`, algoritmo, catálogo e pasta, nessa ordem. |
| MD5/SHA1/SHA256/SHA512 | Mapeamento dos quatro nomes para a JCA e vetores fixos de teste. |
| XML com zero ou mais arquivos e um ou mais digests por arquivo | DOM, catálogo vazio, inclusão no registro existente e quatro algoritmos no mesmo registro. |
| Item 1: conteúdo de todos os arquivos da pasta | Leitura binária por blocos; inclui o catálogo se presente; não percorre subpastas. |
| Item 2: comparação com catálogo e outros arquivos | Compara nome + algoritmo; procura colisões na pasta e em outros nomes do catálogo. |
| Item 3: formato exato e quatro status | Uma linha por arquivo, nome sem caminho, algoritmo, hex e status entre parênteses. |
| Item 4: incluir somente `NOT FOUND` | Novo `DIGEST_ENTRY` no arquivo existente ou novo `FILE_ENTRY` ao final; preserva os outros valores. |
| Observações 3 e 4: `update` e conteúdo, não nome | `update(byte[], int, int)` com a quantidade lida; validado com arquivos binários e vetores conhecidos. |
| Observação 5: argumentos omitidos/insuficientes | Mensagem de uso e encerramento; testes com zero, um e dois argumentos. |
| Observação 2: JDK 1.8 | Compilação e testes em OpenJDK 8; ressalva de distribuição descrita acima. |
| Aviso do EAD: identificação no início de cada `.java` | Cabeçalhos completos no programa e nos testes, com nome e matrícula dos dois integrantes. |

## Códigos de saída

- `0`: processamento concluído;
- `1`: erro de uso, algoritmo da linha de comando ou sintaxe de caminho;
- `2`: erro de catálogo, JCA, permissão, leitura ou escrita, incluindo
  arquivo ou pasta inexistente.

## Limpeza e entrega

Para apagar somente os artefatos gerados localmente:

```powershell
Remove-Item -LiteralPath .\build -Recurse -Force
```

O PDF pede o programa fonte, e o aviso do EAD exige a identificação dos
integrantes no início dos fontes entregues. Neste projeto, a aplicação
completa e suas classes internas estão em **`DigestCalculator.java`**;
esse é o arquivo a enviar diretamente no EAD.

`tests/DigestCalculatorTest.java` é auxiliar para a conferência local.
Os materiais fornecidos não exigem explicitamente a entrega de uma classe de
testes, e a aplicação compila e executa sem esse arquivo.

Os dois fontes possuem o cabeçalho completo com os integrantes:

- Rafael Soares Estevão — matrícula 2320470;
- Dante Honorato Navaza — matrícula 2321406.

Cada integrante deve fazer sua própria submissão no EAD da PUC-Rio.
**Não enviar ZIP, RAR, TGZ ou qualquer outro arquivo compactado.** Também não
enviar `.class`, a pasta `build`, os PDFs/ZIPs das aulas ou o README.

- Prazo de submissão: **28/09/2026, às 13:00**.
- Prazo máximo: **28/09/2026, às 23:59**.
- Valor máximo do T2: **10,0 pontos**, com **peso 1 no G1**.

A compilação e a execução foram validadas no Java 8 conforme o registro de
testes acima.
