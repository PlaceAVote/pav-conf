# pav-conf

Application for easier management of [Convox](http://convox.com)
environment variables, or other options.

## Usage

`java -jar pav-conf.jar` should be enough. Check `pav-conf.edn` for
configuration options.

## Environment credentials

After implementing #3, `pav-conf` is able to store Convox credentials
in more secure manner, as environment variables. The format is in
form:

```
CONVOX_CREDS="NAME1:PASS1; NAME2:PASS2"
```

where `NAME1` and `NAME2` are environment names (in `pav-conf.edn`
under `:name` key) and `PASS1` and `PASS2` are dedicated environment
access passwords.

If you explicitly set `:password` for specific environment,
`CONVOX_CREDS` will not be used for that case.

## License

Copyright © 2016 sz, PlaceAVote
