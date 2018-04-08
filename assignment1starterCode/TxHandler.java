import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TxHandler {

    UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS
        Set<UTXO> usedUTXOs = new HashSet<>();

        double sumOfUTXOOutputs = 0, sumOfTxOutputs = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            // (1) all outputs claimed by {@code tx} are in the current UTXO pool,
            UTXO candidateUTXO = new UTXO(input.prevTxHash, input.outputIndex);

            if (!utxoPool.contains(candidateUTXO)) { return false; }

            // (2) the signatures on each input of {@code tx} are valid,
            Transaction.Output utxoOutput = utxoPool.getTxOutput(candidateUTXO);
            if (!Crypto.verifySignature(
                    utxoOutput.address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }

            // (3) no UTXO is claimed multiple times by {@code tx},
            if (usedUTXOs.contains(candidateUTXO)) { return false; }
            usedUTXOs.add(candidateUTXO);

            sumOfUTXOOutputs += utxoOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            // (4) all of {@code tx}s output values are non-negative, and
            if (output.value < 0) { return false; }
            sumOfTxOutputs += output.value;
        }
        // (5) the sum of {@code tx}s input values is greater than or equal to
        // the sum of its output values; and false otherwise.
        return sumOfUTXOOutputs >= sumOfTxOutputs;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking
     * each
     * transaction for correctness, returning a mutually valid array of accepted
     * transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // TODO IMPLEMENT THIS
        int[] maxLen = new int[1];
        List<Transaction> maxPath = new LinkedList<>();

        Map<byte[], Transaction> hash2Tx = new HashMap<>(possibleTxs.length);
        for (Transaction tx : possibleTxs) { hash2Tx.put(tx.getHash(), tx); }

        for (Transaction tx : possibleTxs) {
            dfs(tx, hash2Tx, maxLen, maxPath, 0, new LinkedList<>());
        }
        if (maxLen[0] > 0) {
            for (Transaction tx : maxPath) { applyTx(tx); }
        }
        return maxPath.toArray(new Transaction[maxLen[0]]);
    }

    private void applyTx(Transaction tx) {
        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(utxo);
        }

        for (int i = 0; i < tx.getOutputs().size(); i++) {
            utxoPool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i));
        }
    }
}
