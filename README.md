# Xlite Wallet Backend

The Xlite Wallet Backend is a Java-based project that serves as the backend infrastructure for the Xlite wallet application. It is built using Java with JDK version 21 and utilizes Maven for build automation. The project incorporates the org.bitcoinj library version 0.14.7 for Bitcoin-related functionality.

## Table of Contents

- [Project Overview](#project-overview)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Maven Build Commands](#maven-build-commands)
- [Usage](#usage)
- [Configuration](#configuration)
- [Contributing](#contributing)
- [License](#license)

## Project Overview

XLite is a local wallet daemon used by a wallet application and by named
Blocknet compatibility flows. It owns wallet keys and exposes an authenticated
JSON-RPC boundary to local clients.

## Prerequisites

List the prerequisites required to set up and run the Xlite Wallet Backend. Include the following:

- JDK 21: Install the Java Development Kit version 21 or a compatible version.
- Maven 3.8.6 or higher: Install Apache Maven for build automation.

## Getting Started

Provide step-by-step instructions on how to set up and run the Xlite Wallet Backend locally. Include the following:

1. Clone the repository:
```
git clone https://github.com/sh-rn/xlite-daemon
```
2. Build the project:
```
cd xlite-daemon

# Make Maven wrapper executable (mac/linux):
chmod +x mvnw

# Build native image:
./mvnw clean package -Pnative

# Or for faster build without tests:
./mvnw clean package -Pnative-fast
```
3. Configuration: If any configuration files or settings need to be modified, provide instructions on how to set them up.

4. Run the application:
```
./target/xlite-daemon
```

## Maven Build Commands

### Basic Maven Operations

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package JAR (without native compilation)
mvn package -DskipTests

# Build native image
mvn clean package -Pnative

# Run application
mvn exec:java

# Skip tests for faster builds
mvn clean package -Pnative-fast
```

### Profile-Specific Commands

- **native**: Full native image compilation with all optimizations
- **native-fast**: Faster native compilation with reduced optimizations for development

### Common Maven Goals

- `mvn clean`: Remove build artifacts
- `mvn compile`: Compile source code
- `mvn test`: Run unit tests
- `mvn package`: Package compiled code into distributable format
- `mvn install`: Install package into local repository
- `mvn dependency:tree`: Display dependency tree
```
## Usage

Explain how to use the Xlite Wallet Backend. Provide information on available APIs, endpoints, or functionalities. Include any code snippets or examples to demonstrate usage patterns.

https://docs.blocknet.org/xlite/access-coin-daemons-via-rpc/

Secrets are never accepted through environment variables or command-line
arguments. Passwords and mnemonics are entered through stdin only. The legacy
argument menu is disabled, and mnemonic export is disabled. Encrypted backup
and restore is not provided by this patch.

RPC servers bind to loopback only and require the configured local RPC
credentials. Do not expose the ports beyond the local machine.

Supported RPC calls
```
Calls requiring XRouter calls: getblockhash, getblock, gettransaction
help - This command help.
stop - Shutdown the server
=====Blockchain=====
gettxout <txid> <vout> - Get info about an unspent transaction output
=====Network=====
getinfo - Get information such as balances, protocol version, and more
getnetworkinfo - Get network information
getrawmempool - Get raw mempool
getblockchaininfo - Get blockchain info
getblockhash <height> - Get the hash of a block at a given height
getblock <hash> - Get a block's JSON representation given its hash
=====Wallet=====
listunspent - Get all UTXOs in the wallet
getnewaddress - Generate a new address
gettransaction <txid> - Get a transaction given its TXID
getaddressesbyaccount <account> - Get addresses belonging to a given account. The only account available is 'main' which contains all addresses
importprivkey <privkey> - Import a key for local administrator recovery (not persistent; never expose remotely)
dumpprivkey <address> - Export a key for local administrator backup (protect the output as wallet secret material)
=====Utilities=====
signmessage <address> <message> - Sign a message with a given address' private key
verifymessage <address> <signature> <message> - Verify a signature for a message signed by a given address
=====Raw Transactions=====
createrawtransaction <inputs> <outputs> - Create a raw transaction given inputs and outputs in JSON format. For more info, run createrawtransaction with no arguments.
decoderawtransaction <rawtx> - Get a raw transaction's JSON representation
signrawtransaction <rawtx> - Unavailable until an intent-bound Unified adapter is present
sendrawtransaction <rawtx> - Unavailable until an intent-bound Unified adapter is present
```

Outside managed read-only mode, the advanced `importprivkey` and `dumpprivkey`
methods are retained as authenticated local wallet-administrator operations for
backup and recovery.
They return or accept raw private-key material and must never be exposed to a
renderer or an unrestricted RPC proxy. `sendtransaction` is not available.
`signmessage` is restricted to an exact wallet-owned self-address proof or a
canonical Core `UtxoEntry` proof bound to an unspent wallet-owned output. The
canonical proof is `<lowercase-64-hex-txid>:<uint32-vout>:<Core-default-float-amount>:<same-address>`;
uppercase transaction IDs are rejected rather than normalised, and Core's
two-digit exponent form is required at exponent boundaries (for example,
`1e-05` and `1e+06`). `signrawtransaction` and `sendrawtransaction` fail
closed with method-not-found until an intent-bound Unified adapter is present.
In `--read-only-existing-profile` mode, generic raw construction/sign/send,
address creation, key import/export, mnemonic export, message signing and all
transfer/send aliases return the same method-not-found response before their
parameters are inspected. Existing persisted HD addresses are derived only
during startup and do not increment or rewrite the configured address count.

## Configuration

Describe any configuration options available for the Xlite Wallet Backend. Explain the purpose of each configuration file or setting and how to modify them as needed. Include instructions on any environment variables or external configurations required for proper functioning.

one file per coin,
xlite-daemon (Backend) Configuration Files:

```
Windows
%appdata%\CloudChains\settings\config-*.json

MacOS
~/Library/Application Support/CloudChains/settings/config-*.json

Linux
~/.config/CloudChains/settings/config-*.json
```

## Contributing


Explain how others can contribute to the Xlite Wallet Backend project. Describe the guidelines for submitting bug reports, feature requests, or code contributions. Include information on how to set up the development environment, coding conventions, and the contribution workflow.

## License

Specify the license under which the Xlite Wallet Backend project is released. Choose an appropriate license that suits your project's requirements. If you're not sure, consult with your team or a legal professional.
