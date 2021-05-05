import java.util.ArrayList;
import java.util.HashMap;

/* Block Chain should maintain only limited block nodes to satisfy the functions
   You should not have the all the blocks added to the block chain in memory 
   as it would overflow memory
 */

public class BlockChain {
   public static final int CUT_OFF_AGE = 10;

   // all information required in handling a block in block chain
   private class BlockNode {
      public Block b;
      public BlockNode parent;
      public ArrayList<BlockNode> children;
      public int height;
      // utxo pool for making a new block on top of this block
      private UTXOPool uPool;

      public BlockNode(Block b, BlockNode parent, UTXOPool uPool) {
         this.b = b;
         this.parent = parent;
         children = new ArrayList<BlockNode>();
         this.uPool = uPool;
         if (parent != null) {
            height = parent.height + 1;
            parent.children.add(this);
         } else {
            height = 1;
         }
      }

      public UTXOPool getUTXOPoolCopy() {
         return new UTXOPool(uPool);
      }
   }

   // Used to store nodes into block chain with key/value pairs
   private HashMap<ByteArrayWrapper, BlockNode> blockChain;

   // An object of BlockNode to manipulate max height node
   private BlockNode maxHeightNode;

   // An object of TransactionPool to manimpulate in the next functions
   private TransactionPool txPool;

   /*
    * create an empty block chain with just a genesis block. Assume genesis block
    * is a valid block
    */
   public BlockChain(Block genesisBlock) {
      blockChain = new HashMap<>();
      UTXOPool uPool = new UTXOPool();

      // Adding the block to UTXO pool
      addCoinbaseToUTXOPool(genesisBlock, uPool);

      BlockNode genesisNode = new BlockNode(genesisBlock, null, uPool);

      // Adding into the blockchain, the genesis node into genesis block array
      blockChain.put(wrap(genesisBlock.getHash()), genesisNode);

      txPool = new TransactionPool();

      // Setting genesis node as max height node
      maxHeightNode = genesisNode;
   }

   /*
    * Get the maximum height block
    */
   public Block getMaxHeightBlock() {
      return maxHeightNode.b;
   }

   /*
    * Get the UTXOPool for mining a new block on top of max height block
    */
   public UTXOPool getMaxHeightUTXOPool() {
      return maxHeightNode.getUTXOPoolCopy();
   }

   /*
    * Get the transaction pool to mine a new block
    */
   public TransactionPool getTransactionPool() {
      return txPool;
   }

   /*
    * Add a block to block chain if it is valid. For validity, all transactions
    * should be valid and block should be at height > (maxHeight - CUT_OFF_AGE).
    * For example, you can try creating a new block over genesis block (block
    * height 2) if blockChain height is <= CUT_OFF_AGE + 1. As soon as height >
    * CUT_OFF_AGE + 1, you cannot create a new block at height 2. Return true of
    * block is successfully added
    */
   public boolean addBlock(Block b) {

      // Initializing byte array oforevious block hashes
      byte[] prevBlockHash = b.getPrevBlockHash();

      // If previous block hash is empty return false
      if (prevBlockHash == null)
         return false;

      // Setting the key/value of previous block hash as parent node
      BlockNode parentNode = blockChain.get(wrap(prevBlockHash));

      // If parent node is empty return false
      if (parentNode == null) {
         return false;
      }

      // Creating a TxHandler object, handler, which will hold the parent node's UTXO
      // pool
      TxHandler handler = new TxHandler(parentNode.getUTXOPoolCopy());

      // Creating an array of transactions
      Transaction[] transactions = b.getTransactions().toArray(new Transaction[0]);

      // Adding into valid transaction array the transactions from handler
      Transaction[] validTransactions = handler.handleTxs(transactions);

      // If length of valid transactions doesn't equal transactions length return
      // false. They should be equal
      if (validTransactions.length != transactions.length) {
         return false;
      }

      // If new height is less than maxHeightBode - CUT_OFF_AGE, return false
      int newHeight = parentNode.height + 1;
      if (newHeight <= maxHeightNode.height - CUT_OFF_AGE) {
         return false;
      }

      // Adding UTXO pool to coinbase
      UTXOPool uPool = handler.getUTXOPool();
      addCoinbaseToUTXOPool(b, uPool);

      // Adding node to block chain
      BlockNode node = new BlockNode(b, parentNode, uPool);
      blockChain.put(wrap(b.getHash()), node);

      // If new height is greater than maxHeightNode set maxHeightNode as node
      if (newHeight > maxHeightNode.height) {
         maxHeightNode = node;
      }
      return true;
   }

   /*
    * Add a transaction in transaction pool
    */
   public void addTransaction(Transaction tx) {
      txPool.addTransaction(tx);
   }

   // Function to add coinbase to UTXO pool
   private void addCoinbaseToUTXOPool(Block b, UTXOPool uPool) {
      Transaction coinbase = b.getCoinbase();
      for (int i = 0; i < coinbase.numOutputs(); i++) {
         Transaction.Output out = coinbase.getOutput(i);
         UTXO utxo = new UTXO(coinbase.getHash(), i);
         uPool.addUTXO(utxo, out);
      }
   }

   // Wrappping byte array into buffer
   private static ByteArrayWrapper wrap(byte[] arr) {
      return new ByteArrayWrapper(arr);
   }
}
