package hamlet.tree;

import hamlet.tree.entity.Tree;

import javax.persistence.*;
import java.util.List;
import java.util.Scanner;

public class TreeApplication {

    private static final EntityManagerFactory FACTORY;

    static {
        FACTORY = Persistence.createEntityManagerFactory("main");
    }

    private static final Scanner IN = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("""
                Добавить категорию [1]
                Переместить категорию [2]
                Удалить катеорию [3]
                Выберите действие: \
                """);
        String actionNum = IN.nextLine();
        switch (actionNum) {
            case "1" -> create();
            case "2" -> move();
            case "3" -> remove();
            default -> System.out.println("Такого дейтввия не сущестует");
        }
    }

    private static void create() {
        EntityManager manager = FACTORY.createEntityManager();
        try {
            manager.getTransaction().begin();
            printTree();
            System.out.println("Id категория для добвалния либо 0 (новая категрия): ");
            long parentId = Long.parseLong(IN.nextLine());

            Tree newTree = new Tree();
            System.out.print("Введите название: ");
            String name = IN.next();
            newTree.setName(name);

            if (parentId > 0) {
                Tree parent = manager.find(Tree.class, parentId);
                Query updateLKQuery = manager.createQuery(
                        "update Tree t set t.leftKey = t.leftKey + 2 where t.leftKey > ?1"
                );
                updateLKQuery.setParameter(1, parent.getRightKey());
                updateLKQuery.executeUpdate();

                Query updateRKQuery = manager.createQuery(
                        "update Tree t set t.rightKey = t.rightKey + 2 where t.rightKey >= ?1"
                );
                updateRKQuery.setParameter(1, parent.getRightKey());
                updateRKQuery.executeUpdate();

                newTree.setLeftKey(parent.getRightKey());
                newTree.setRightKey(parent.getRightKey() + 1);
                newTree.setLevel(parent.getLevel() + 1);
            } else if (parentId == 0) {
                TypedQuery<Integer> maxRKQuery = manager.createQuery(
                        "select max(t.rightKey) from Tree t", Integer.class
                );
                newTree.setLeftKey(maxRKQuery.getSingleResult() + 1);
                newTree.setRightKey(maxRKQuery.getSingleResult() + 2);
                newTree.setLevel(1);
            }

            manager.persist(newTree);
            manager.getTransaction().commit();
        } catch (Exception e) {
            manager.getTransaction().rollback();
            e.printStackTrace();
        } finally {
            manager.close();
        }
    }

    private static void move() {
        EntityManager manager = FACTORY.createEntityManager();
        try {
            manager.getTransaction().begin();
            System.out.println("Введите id категории которую хотите переместить: ");
            long moveTreeId = Long.parseLong(IN.nextLine());
            Tree moveTree = manager.find(Tree.class, moveTreeId);

            Query toNegativeQuery = manager.createQuery("""
                    update Tree t set t.leftKey = 0 - t.leftKey, t.rightKey = 0 - t.rightKey \
                    where t.leftKey >= ?1 and t.rightKey <= ?2
                    """);
            toNegativeQuery.setParameter(1, moveTree.getLeftKey());
            toNegativeQuery.setParameter(2, moveTree.getRightKey());
            toNegativeQuery.executeUpdate();

            int moveTreeSize = moveTree.getRightKey() - moveTree.getLeftKey() + 1;

            Query removeLKSpaceQuery = manager.createQuery(
                    "update Tree t set t.leftKey = t.leftKey - ?1 where t.leftKey > ?2"
            );
            removeLKSpaceQuery.setParameter(1, moveTreeSize);
            removeLKSpaceQuery.setParameter(2, moveTree.getRightKey());
            removeLKSpaceQuery.executeUpdate();

            Query removeRKSpaceQuery = manager.createQuery(
                    "update Tree t set t.rightKey = t.rightKey - ?1 where t.rightKey > ?2"
            );
            removeRKSpaceQuery.setParameter(1, moveTreeSize);
            removeRKSpaceQuery.setParameter(2, moveTree.getRightKey());
            removeRKSpaceQuery.executeUpdate();

            System.out.println("Введите id, куда перемещать либо 0 для создания нового элемента: ");
            long targetId = Long.parseLong(IN.nextLine());
            if (targetId > 0) {
                Tree target = manager.find(Tree.class, targetId);

                Query allocLKSpaceQuery = manager.createQuery(
                        "update Tree t set t.leftKey = t.leftKey + ?1 where t.leftKey > ?2"
                );
                allocLKSpaceQuery.setParameter(1, moveTreeSize);
                allocLKSpaceQuery.setParameter(2, target.getRightKey());
                allocLKSpaceQuery.executeUpdate();

                Query allocRKSpaceQuery = manager.createQuery(
                        "update Tree t set t.rightKey = t.rightKey + ?1 where t.rightKey >= ?2"
                );
                allocRKSpaceQuery.setParameter(1, moveTreeSize);
                allocRKSpaceQuery.setParameter(2, target.getRightKey());
                allocRKSpaceQuery.executeUpdate();

                manager.refresh(target);

                Query toTargetQuery = manager.createQuery("""
                        update Tree t \
                        set t.leftKey = 0 - t.leftKey + ?1, \
                            t.rightKey = 0 - t.rightKey + ?1, \
                            t.level = t.level - ?2 + 1 \
                        where t.leftKey < 0 \
                        """);
                toTargetQuery.setParameter(1, target.getRightKey() - moveTree.getRightKey() - 1);
                toTargetQuery.setParameter(2, moveTree.getLevel() - target.getLevel());
                toTargetQuery.executeUpdate();
            } else if (targetId == 0) {
                TypedQuery<Integer> maxRKQuery = manager.createQuery(
                        "select max(t.rightKey) from Tree t", Integer.class
                );

                Query toTarget = manager.createQuery("""
                        update Tree t
                        set t.leftKey = 0 - t.leftKey -?1 + ?2,
                            t.rightKey = 0 - t.rightKey - ?1 + ?2,
                            t.level = t.level - ?3 + 1
                        where t.leftKey < 0
                        """);
                toTarget.setParameter(1, moveTree.getLeftKey());
                toTarget.setParameter(2, maxRKQuery.getSingleResult() + 1);
                toTarget.setParameter(3, moveTree.getLevel());
                toTarget.executeUpdate();
            }
            manager.getTransaction().commit();
        } catch (Exception e) {
            manager.getTransaction().rollback();
            e.printStackTrace();
        } finally {
            manager.close();
        }
    }

    private static void remove() {
        EntityManager manager = FACTORY.createEntityManager();

        try {
            manager.getTransaction().begin();
            printTree();
            System.out.println("Введите id категории которую хотите удалить: ");
            long parentId = Long.parseLong(IN.nextLine());
            Tree parent = manager.find(Tree.class, parentId);

            Query deleteCategoryQuery = manager.createQuery(
                    "delete from Tree t where t.leftKey >= ?1 and t.rightKey <= ?2"
            );
            deleteCategoryQuery.setParameter(1, parent.getLeftKey());
            deleteCategoryQuery.setParameter(2, parent.getRightKey());
            deleteCategoryQuery.executeUpdate();

            int parentSize = parent.getRightKey() - parent.getLeftKey() + 1;

            Query updateLKQuery = manager.createQuery(
                    "update Tree t set t.leftKey = t.leftKey - ?1 where t.leftKey > ?2"
            );
            updateLKQuery.setParameter(1, parentSize);
            updateLKQuery.setParameter(2, parent.getRightKey());
            updateLKQuery.executeUpdate();

            Query updateRKQuery = manager.createQuery(
                    "update Tree t set t.rightKey = t.rightKey - ?1 where t.rightKey >= ?2"
            );
            updateRKQuery.setParameter(1, parentSize);
            updateRKQuery.setParameter(2, parent.getRightKey());
            updateRKQuery.executeUpdate();
            manager.getTransaction().commit();
        } catch (Exception e) {
            manager.getTransaction().rollback();
            e.printStackTrace();
        } finally {
            manager.close();
        }
    }

    private static void printTree() {
        EntityManager manager = FACTORY.createEntityManager();
        TypedQuery<Tree> query = manager.createQuery(
                "select t from Tree t order by t.leftKey", Tree.class
        );
        List<Tree> trees = query.getResultList();
        for (Tree tree : trees) {
            String dashes = "- ".repeat(tree.getLevel());
            System.out.println(dashes + tree.getName() + " [" + tree.getId() + "]");
        }
        manager.close();
    }
}
