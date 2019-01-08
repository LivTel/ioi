Robotic instrument control software for the Infra-red IO:I instrument.

IO:I is an Infra-red instrument for the Liverpool Telescope. It uses an Hawaii H2RG infra-red array, which is driven by Hawaii's HxRG testing software (written in IDL). This has a control socket which this software writes to (using commands in ngat.ioi.command.*) to control the array. The software exports a standard instrument robotic control layer, which the RCS talks to to control IO:I.
