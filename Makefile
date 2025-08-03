# Diretórios
SRC_DIR=src
BIN_DIR=bin
LIB_DIR=lib

# Classe principal
MAIN_CLASS=main.Main

# Lista de fontes
SOURCES=$(shell find $(SRC_DIR) -name "*.java")

# URLs das dependências
GSON_URL=https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar
NETTY_URL=https://repo1.maven.org/maven2/io/netty/netty-all/4.1.68.Final/netty-all-4.1.68.Final.jar
BCPROV_URL=https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.68/bcprov-jdk15on-1.68.jar
BCPKIX_URL=https://repo1.maven.org/maven2/org/bouncycastle/bcpkix-jdk15on/1.68/bcpkix-jdk15on-1.68.jar

# Dependências
DEPENDENCIES=$(LIB_DIR)/gson-2.8.9.jar $(LIB_DIR)/netty-all-4.1.68.Final.jar $(LIB_DIR)/bcprov-jdk15on-1.68.jar $(LIB_DIR)/bcpkix-jdk15on-1.68.jar

# Classpath
CLASSPATH=$(BIN_DIR):$(LIB_DIR)/*

JAVA_CMD = java -cp $(BIN_DIR):$(LIB_DIR)/* main.Main

# Targets
all: $(DEPENDENCIES) compile

$(LIB_DIR):
	mkdir -p $(LIB_DIR)

$(LIB_DIR)/gson-2.8.9.jar: | $(LIB_DIR)
	wget -q -O $@ $(GSON_URL)

$(LIB_DIR)/netty-all-4.1.68.Final.jar: | $(LIB_DIR)
	wget -q -O $@ $(NETTY_URL)

$(LIB_DIR)/bcprov-jdk15on-1.68.jar: | $(LIB_DIR)
	wget -q -O $@ $(BCPROV_URL)

$(LIB_DIR)/bcpkix-jdk15on-1.68.jar: | $(LIB_DIR)
	wget -q -O $@ $(BCPKIX_URL)

compile:
	mkdir -p $(BIN_DIR)
	javac -cp $(BIN_DIR):$(LIB_DIR)/* -d $(BIN_DIR) -sourcepath $(SRC_DIR) \
    src/auction/*.java \
    src/consensus/*.java \
    src/ledger/*.java \
    src/main/*.java \
    src/network/*.java \
    src/network/kad/*.java \
    src/network/netty/*.java \
    src/util/*.java


SESSION_NAME := SSD_PROJECT

run: compile
	@echo "🚀 Abrindo 5 janelas de terminal rodando o Main..."
	gnome-terminal -- bash -c "java -cp $(CLASSPATH) $(MAIN_CLASS); exec bash" &
	gnome-terminal -- bash -c "java -cp $(CLASSPATH) $(MAIN_CLASS); exec bash" &
	gnome-terminal -- bash -c "java -cp $(CLASSPATH) $(MAIN_CLASS); exec bash" &
	gnome-terminal -- bash -c "java -cp $(CLASSPATH) $(MAIN_CLASS); exec bash" &
	gnome-terminal -- bash -c "java -cp $(CLASSPATH) $(MAIN_CLASS); exec bash" &


clean:
	rm -rf $(BIN_DIR) $(LIB_DIR)
