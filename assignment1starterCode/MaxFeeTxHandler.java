import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class MaxFeeTxHandler {

    UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
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

    //private double calculateTxFee(Transaction tx) {
    //
    //    double inputSum = tx.getInputs().stream()
    //            .map(input -> new UTXO(input.prevTxHash, input.outputIndex))
    //            .filter(utxo -> utxoPool.contains(utxo) && isValidTx(tx))
    //            .mapToDouble(utxo -> utxoPool.getTxOutput(utxo).value)
    //            .sum();
    //
    //    double outputSum = tx.getOutputs().stream()
    //            .mapToDouble(output -> output.value)
    //            .sum();
    //
    //    return inputSum - outputSum;
    //}

    private double calculateTxFee(Transaction tx) {
        double sumInputs = 0;
        double sumOutputs = 0;
        for (Transaction.Input in : tx.getInputs()) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            if (!utxoPool.contains(utxo) || !isValidTx(tx)) { continue; }
            Transaction.Output txOutput = utxoPool.getTxOutput(utxo);
            sumInputs += txOutput.value;
        }
        for (Transaction.Output out : tx.getOutputs()) {
            sumOutputs += out.value;
        }
        return sumInputs - sumOutputs;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking
     * each
     * transaction for correctness, returning a mutually valid array of accepted
     * transactions, and
     * updating the current UTXO pool as appropriate.
     */
    //public Transaction[] handleTxs(Transaction[] possibleTxs) {
    //    Arrays.sort(possibleTxs, Comparator.comparingDouble(this::calculateTxFee).reversed());
    //    Set<Transaction> validTxSet = new HashSet<>();
    //
    //    for (Transaction tx : possibleTxs) {
    //        if (isValidTx(tx)) {
    //            applyTx(tx);
    //            validTxSet.add(tx);
    //        }
    //    }
    //    return validTxSet.toArray(new Transaction[0]);
    //}
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        Set<Transaction> txsSortedByFees = new TreeSet<>(
                (tx1, tx2) -> Double.compare(calculateTxFee(tx2), calculateTxFee(tx1))
        );

        Collections.addAll(txsSortedByFees, possibleTxs);

        Set<Transaction> acceptedTxs = new HashSet<>();
        for (Transaction tx : txsSortedByFees) {
            if (isValidTx(tx)) {
                acceptedTxs.add(tx);
                for (Transaction.Input in : tx.getInputs()) {
                    UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
                    utxoPool.removeUTXO(utxo);
                }
                for (int i = 0; i < tx.numOutputs(); i++) {
                    Transaction.Output out = tx.getOutput(i);
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    utxoPool.addUTXO(utxo, out);
                }
            }
        }

        Transaction[] validTxArray = new Transaction[acceptedTxs.size()];
        return acceptedTxs.toArray(validTxArray);
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
