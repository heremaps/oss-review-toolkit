# License handling

ORT deals with various types of licenses. Their ORT-specific names and purposes are explained in detail below.

## Declared license

For ORT, declared licenses are those licenses which are declared as part of package manager metadata, and as gathered
by the ORT *analyzer*. In other words, this is the license the author of the package claims or intends the package to
be licensed under; the license which is "visible from the outside". However, the declared license alone only provides an
incomplete picture, e.g. in so-called "envelope cases" where the license visible from the outside on the envelope does
not match what is inside the envelope, i.e. in the source code. So, license information e.g. inside a `LICENSE` file is
*not* a declared license in ORT-sense.

## Detected license

The detected licenses are those being detected via an ORT *scanner* implementation by looking at the source code, where
"source code" here refers to the whole code base of the package, including the contents of `LICENSE` files or license /
copyright headers in source files. Detected licenses complement the picture created by 

## Concluded license

TODO

## Resolved license

TODO

## Effective license

TODO

