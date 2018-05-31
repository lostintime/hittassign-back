HIT - Simple Crawling DSL
=========================

Simple web crawling DSL interpreter.

## Grammar

This DSL supports few statements and uses indentation for blocks.

### Debug

Prints given message, for debugging

```
debug Hello\ World
```

### Fetch

Fetches JSON from given resource and binds value to a named variable

```
fetch c https://someapi.com/cities.json
  debug Total:\ {c.length}\ cities
```

Where:

  * `c` variable name
  * `https://someapi.com/cities.json` JSON source

### Foreach

Iterate over values at given JSON path (must be an Array).

```
foreach row c.images
  debug Row.id:\ {row.id}
```

Where:

  * `row` variable name for element value
  * `c.images` list variable reference (name: `c` and `$.images` json path)


### Download

Downloads remote file to local path.

```
download https://somapi.com/cities/{c.id}.json /tmp/cities/{c.id}.json
```

Where: 

  * `https://somapi.com/cities/{c.id}.json` source file url
  * `/tmp/cities/{c.id}.json` path to local file

### Concurrently

Set concurrency level for child block statements.

```
concurrently 8
  debug Concurrency\ level\ is\ 8\ here
```

## Script Example

```
debug Starting...

fetch h https://somedmoeapi.com/some/list.json
  concurrently 8
    foreach c h.cities
      debug id:\ {c.id}
      download https://publiccitiesapi.com/{c.id}/image.png /tmp/cities/{c.id}/image.png

debug Done.
```


## Run with docker

```bash
cat sample.hit | docker run -i --rm lostintime/hittasign-back:1.0
```

## License

All code in this repository is licensed under the Apache License,
Version 2.0.  See [LICENCE](./LICENSE).

