# DigestCalculator

Implementação do Trabalho 2 da disciplina INF1416 — Segurança da Informação.
O programa usa a Java Cryptography Architecture (JCA) para calcular o digest do
conteúdo de todos os arquivos regulares encontrados diretamente em uma pasta,
compará-los com um catálogo XML e registrar digests ainda desconhecidos.

Não há interface gráfica nem dependências externas.

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
- PowerShell somente para executar os exemplos de comandos abaixo.

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

## Execução

O programa recebe exatamente três argumentos:

```text
java DigestCalculator <MD5|SHA1|SHA256|SHA512> <arquivo_catalogo_xml> <pasta_arquivos>
```

Com as classes compiladas em `build`:

```powershell
java -cp .\build DigestCalculator SHA256 ".\dados\catalogo.xml" ".\dados\arquivos"
```

Use aspas nos caminhos que contenham espaços. O catálogo XML pode ficar dentro
da pasta processada: nesse caso ele mesmo é ignorado na listagem, já que é
modificado pelo próprio programa.

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

Violações de estrutura interrompem a execução sem alterar o arquivo:

- a raiz deve ser `CATALOG`;
- cada `FILE_ENTRY` deve ter exatamente um `FILE_NAME` e um ou mais
  `DIGEST_ENTRY`;
- cada `DIGEST_ENTRY` deve ter um `DIGEST_TYPE` e um `DIGEST_HEX`;
- `FILE_NAME` deve conter somente o nome, sem caminho;
- DTDs e entidades externas são rejeitados.

Problemas no conteúdo de uma entrada geram apenas um aviso em stderr, e o
processamento continua:

- `DIGEST_TYPE` desconhecido: aquele `DIGEST_ENTRY` é ignorado;
- mesmo tipo de digest repetido para um arquivo: vale o primeiro;
- `DIGEST_HEX` com tamanho errado ou caracteres não hexadecimais: o valor é
  mantido e nunca coincide com um digest calculado, logo o arquivo fica
  `NOT OK`.

Um `FILE_NAME` repetido em mais de um `FILE_ENTRY` tem seus digests reunidos,
e novos digests são acrescentados ao primeiro desses registros.

## Cálculo e classificação

Os arquivos são lidos em blocos binários de 8192 bytes por meio de
`MessageDigest.update(byte[], int, int)`. Portanto, o digest corresponde ao
conteúdo do arquivo, e não ao seu nome, e arquivos grandes não precisam ser
carregados inteiros na memória.

A classificação segue esta precedência:

1. `COLISION`: o digest calculado coincide com o digest de outro nome no
   catálogo ou com outro arquivo da pasta;
2. `OK`: o catálogo contém o mesmo nome, algoritmo e digest;
3. `NOT OK`: o catálogo contém o mesmo nome e algoritmo, mas outro digest;
4. `NOT FOUND`: não existe digest catalogado para aquela combinação de nome e
   algoritmo.

A grafia `COLISION`, com um único `L`, reproduz exatamente o enunciado.
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
classificados com sucesso. Se não houver `NOT FOUND`, nenhuma escrita é feita.
Ao regravar, a indentação original é descartada e o arquivo inteiro é
reindentado com 4 espaços por nível, com a declaração XML na primeira linha,
para que o formato não se degrade a cada execução.

## Decisões de escopo

- somente arquivos regulares diretamente na pasta são processados;
- subpastas não são percorridas;
- o próprio arquivo de catálogo é ignorado se estiver dentro da pasta;
- nomes de arquivo são comparados exatamente, inclusive maiúsculas e
  minúsculas;
- a ordem da saída é determinística;
- catálogo estruturalmente malformado é rejeitado sem alteração;
- se não houver nenhum `NOT FOUND`, o catálogo não é regravado.

## Testes automatizados

Os testes não exigem JUnit. Para compilá-los com JDK 8:

```powershell
New-Item -ItemType Directory -Force .\build | Out-Null
javac -encoding UTF-8 -source 1.8 -target 1.8 -d .\build .\DigestCalculator.java .\tests\DigestCalculatorTest.java
java -cp .\build DigestCalculatorTest
```

Em JDK 9 ou posterior:

```powershell
javac --release 8 -encoding UTF-8 -Xlint:all -d .\build .\DigestCalculator.java .\tests\DigestCalculatorTest.java
java -cp .\build DigestCalculatorTest
```

Os cenários verificam:

- `OK`, `NOT OK`, `NOT FOUND` e `COLISION` na mesma execução;
- colisão entre arquivos da pasta e colisão com outro nome no catálogo;
- inclusão em nome existente e criação de novo `FILE_ENTRY` ao final;
- transformação de `NOT FOUND` em `OK` na segunda execução;
- ausência de regravação quando o catálogo não muda;
- MD5, SHA1, SHA256 e SHA512, inclusive sobre arquivo vazio;
- arquivo binário maior que o buffer de leitura;
- argumentos, caminhos e algoritmo inválidos;
- pasta vazia e catálogo dentro da pasta processada;
- lista vazia, só com espaços ou só com BOM;
- `DIGEST_HEX` inválido, `DIGEST_TYPE` desconhecido, tipo repetido e
  `FILE_NAME` repetido, sem abortar a execução;
- indentação uniforme do XML regravado a partir do layout do enunciado;
- XML estruturalmente inválido e tentativa de XXE;
- execução do `main` em outro processo Java, incluindo seus códigos de saída.

Resultados obtidos no JDK 17 local, compilando com `--release 8`, e no Eclipse
Temurin OpenJDK `1.8.0_504`, usando `javac 1.8.0_504`:

```text
[OK] fluxo completo e persistencia
[OK] quatro algoritmos e arquivo vazio
[OK] arquivo binario maior que o buffer
[OK] validacao dos argumentos e caminhos
[OK] lista vazia equivale a catalogo sem entradas
[OK] entradas problematicas geram aviso sem abortar
[OK] formatacao do XML regravado
[OK] validacao estrutural e seguranca do XML
[OK] main em processo Java separado

Resumo: 9 teste(s) aprovado(s), 0 teste(s) reprovado(s).
```

## Códigos de saída

- `0`: processamento concluído;
- `1`: erro de uso, algoritmo ou caminho;
- `2`: erro de XML, JCA, permissão, leitura ou escrita.

## Limpeza e entrega

Para apagar somente os artefatos gerados localmente:

```powershell
Remove-Item -LiteralPath .\build -Recurse -Force
```

O arquivo obrigatório para submissão é `DigestCalculator.java`. A compilação e
os testes já foram validados no Java 8; confirme no EAD se o professor deseja
também o README ou um arquivo compactado.
