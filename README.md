# Blockchain-Based Auction System

## Project Overview

This project implements a decentralized auction system using blockchain technology. It ensures transparency, immutability, and security for auction transactions and participant interactions. The system supports multiple consensus algorithms and protects against common attacks such as Sybil and Eclipse attacks.

---

## Features

- Decentralized auction management on a blockchain.
- Dynamic consensus algorithm switching (Proof of Work, Proof of Reputation).
- Peer-to-peer network with Kademlia DHT.
- Security features including peer reputation and attack simulation.
- Signed blockchain blocks and transactions.

---

## Installation

### Requirements

- Java JDK 22
- GNU Make
- `wget` utility (for downloading dependencies)
- `gnome-terminal` (for running multiple instances, optional)

### Build and Run

This project uses a `Makefile` to handle dependency downloads, compilation, and execution.

#### Steps:

1. Open a terminal in the project root directory.

2. Run:

   ```sh
   make all
   ```

   This will:
   - Create the `lib` directory.
   - Download necessary JAR dependencies (Gson, Netty, BouncyCastle).
   - Compile the Java source files into the `bin` directory.

3. To run 5 instances of the application in separate terminal windows, execute:

   ```sh
   make run
   ```

   This opens 5 `gnome-terminal` windows running the main class.

4. To clean compiled classes and downloaded dependencies, run:

   ```sh
   make clean
   ```

---

## Usage

- The system initializes a peer-to-peer network server.
- Peers communicate blockchain data and auction information.
- Supports adding new peers and broadcasting blocks and transactions.
- Includes utilities for simulating network attacks to test resilience.

---

## Project Structure

- `src/`: Java source code organized by functionality.
- `bin/`: Compiled `.class` files.
- `lib/`: Downloaded dependency JARs.
- `Makefile`: Automation script for build and run.

---

## Contributing

Contributions are welcome! Please open issues or pull requests for enhancements or bug fixes.

---

## License

MIT License

---

## Contact

For questions or support, please contact the maintainer.

