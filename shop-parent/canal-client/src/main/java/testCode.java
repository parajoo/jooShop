
import java.util.LinkedList;
import java.util.Queue;
public class testCode {
    static class TreeNode {//标准二叉树节点定义
        int val;
        TreeNode left;//先声明类型名，再定义引用
        TreeNode right;

        TreeNode(int val) {
            this.val = val;
        }
    }

    static class State{//节点状态定义
        TreeNode node;
        int depth;

        State(TreeNode node, int depth){
            this.node = node;
            this.depth = depth;
        }
    }

    void levelOrderTraverse(TreeNode root){
        if(root == null) return;
        Queue<State> q = new LinkedList<>();
        q.offer(new State(root, 1));

        while(!q.isEmpty()){
            State cur = q.poll();
            System.out.println("depth = " + cur.depth +",val = "+ cur.node);
            if(cur.node.left != null){
                q.offer(new State(cur.node.left, cur.depth + 1));
            }
            if(cur.node.right != null){
                q.offer(new State(cur.node.right, cur.depth + 1));
            }
        }
    }

    public static void main(String[] args) {

    }
}
