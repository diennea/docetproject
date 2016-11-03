= Execution =
Simply type command:
```
mvn clean install
```
in order to perform docs validation.

To generate also Lucene's search index use:
```
mvn clean install -Ddocet.docs.noIndex=false
```
Index will be created in directory `target/index`.

Finally, to generate a zip archive of docs, adopt:
```
mvn clean install -Ddocet.docs.noZip=false
```
A zip file named `docetdocs.zip` will be created in `target`. In case indexing was enabled, archive will contain the index folder as well.

= Build PDF Version of Documents = 
Coming soon...

= Miscellaneus info =
This section contains a miscellaneous and (so far) unordered set of info regarding usage of Docet to write down docs.

* Do not use "_" char to name pages and images in general as this is used by the rendering engine as a special separator.

* a lot more to be added (unluckily).