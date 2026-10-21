package dev.sporecell.core;

import java.util.*;

/** Coordinates are local X/Z; direction 0 points north (-Z), then clockwise. */
public record Blueprint(List<Piece> pieces) {
    public static final int RADIUS = 4;
    public record Piece(int x,int z,Part part,int direction,boolean mounted,double dx,double dy,double dz,double rotation,Double scale,double pitch,double roll) {
        public Piece(int x,int z,Part part,int direction,boolean mounted,double dx,double dy,double dz,double rotation,Double scale){this(x,z,part,direction,mounted,dx,dy,dz,rotation,scale,0,0);}
        public Piece(int x,int z,Part part,int direction) {this(x,z,part,direction,false,0,0,0,0,1d);}
        public double size() {return scale==null?1:scale;}
        public double angle() {return direction*90+rotation;}
        public double px() {return x*.65+dx;}
        public double py() {return dy;}
        public double pz() {return z*.65+dz;}
        public Piece transform(double angle,double size) {return new Piece(x,z,part,0,mounted,dx,dy,dz,angle,size,pitch,roll);}
        public Piece orient(double yaw,double pitch,double roll){return new Piece(x,z,part,0,mounted,dx,dy,dz,yaw,size(),pitch,roll);}
        public Piece mirror() {return new Piece(-x,z,part,0,mounted,-dx,dy,dz,-angle(),size(),pitch,-roll);}
        public static Piece attachment(int x,int z,Part part,double dx,double dy,double dz,double angle,double scale) {
            return new Piece(x,z,part,0,true,dx,dy,dz,angle,scale);
        }
    }
    public record Stats(int cost, double health, double speed, double turn, double sight, double damage,
                        boolean herbivore, boolean carnivore) {}

    public Blueprint { pieces = List.copyOf(pieces); }
    public static Blueprint starter() {
        return starter(Evolution.Diet.HERBIVORE);
    }
    public static Blueprint starter(Evolution.Diet diet) {
        return new Blueprint(List.of(new Piece(0, 0, Part.CORE, 0),
                new Piece(0, -1,diet==Evolution.Diet.CARNIVORE?Part.CARNIVORE_MOUTH:
                        diet==Evolution.Diet.OMNIVORE?Part.OMNIVORE_MOUTH:Part.HERBIVORE_MOUTH, 0), new Piece(0, 1, Part.FLAGELLUM, 2)));
    }
    public void validate() {
        if (pieces.isEmpty() || pieces.size() > 96) throw new IllegalArgumentException("Допустимо от 1 до 96 элементов");
        Map<String, Piece> occupied = new HashMap<>();
        int cores = 0;
        for (Piece p : pieces) {
            if (p.part == null || Math.abs(p.x) > RADIUS || Math.abs(p.z) > RADIUS || p.direction < 0 || p.direction > 3)
                throw new IllegalArgumentException("Деталь вне сетки или неверный поворот");
            if(!Double.isFinite(p.dx+p.dy+p.dz+p.rotation+p.size()+p.pitch+p.roll) || p.size()<.5 || p.size()>2
                    || Math.abs(p.dx)>.55 || Math.abs(p.dz)>.55 || p.dy<-.2 || p.dy>.8 || Math.abs(p.rotation)>3600 || Math.abs(p.pitch)>3600 || Math.abs(p.roll)>3600)
                throw new IllegalArgumentException("Недопустимое смещение, поворот или масштаб детали");
            if(p.mounted) {
                if(p.part==Part.CORE||p.part==Part.BODY||p.part==Part.ARMOR)throw new IllegalArgumentException("Тело строится из связанных кубиков");
                continue;
            }
            if(p.dx!=0||p.dy!=0||p.dz!=0||p.size()!=1||p.rotation!=0||p.pitch!=0||p.roll!=0) {
                if(p.part==Part.CORE||p.part==Part.BODY||p.part==Part.ARMOR)throw new IllegalArgumentException("Сегменты тела закреплены на сетке");
            }
            if (occupied.put(key(p.x, p.z), p) != null) throw new IllegalArgumentException("Детали пересекаются");
            if (p.part == Part.CORE) { cores++; if (p.x != 0 || p.z != 0) throw new IllegalArgumentException("Ядро должно быть в центре"); }
        }
        if(occupied.size()>49)throw new IllegalArgumentException("Не более 49 сегментов тела");
        for(Piece p:pieces)if(p.mounted&&!occupied.containsKey(key(p.x,p.z)))throw new IllegalArgumentException("Деталь должна крепиться к телу");
        if (cores != 1) throw new IllegalArgumentException("Нужно одно ядро в центре");
        Set<String> visited = new HashSet<>();
        ArrayDeque<Piece> queue = new ArrayDeque<>(); queue.add(occupied.get(key(0,0)));
        while (!queue.isEmpty()) {
            Piece p = queue.remove(); if (!visited.add(key(p.x,p.z))) continue;
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                Piece next = occupied.get(key(p.x+d[0],p.z+d[1]));
                if (next != null && !visited.contains(key(next.x,next.z))) queue.add(next);
            }
        }
        if (visited.size() != occupied.size()) throw new IllegalArgumentException("Все детали должны соединяться с ядром");
    }
    private static String key(int x,int z) { return x + ":" + z; }
    public Stats stats(Map<Part, PartSpec> specs) {
        validate(); int cost=0; double hp=0, thrust=0, turn=0, sight=0, damage=0;
        boolean herb=false, carn=false;
        for (Piece p : pieces) {
            PartSpec s = Objects.requireNonNull(specs.get(p.part)); cost += s.cost(); hp += s.health();
            // Forward/rear mounting supplies propulsion; side mounting gives less thrust.
            double alignment = .55+.45*Math.abs(Math.cos(Math.toRadians(p.angle())));
            thrust += s.thrust() * alignment;
            turn += s.turn() * (1 + Math.abs(p.x) * .15);
            sight += s.sight(); damage = Math.max(damage,s.damage());
            herb |= p.part == Part.HERBIVORE_MOUTH || p.part==Part.OMNIVORE_MOUTH;
            carn |= p.part == Part.CARNIVORE_MOUTH || p.part==Part.OMNIVORE_MOUTH;
        }
        return new Stats(cost, Evolution.MAX_HEALTH, Math.min(.38,(.075+thrust)/Math.sqrt(pieces.size()*.35)),
                Math.min(18, 3+turn), Math.min(96,20+sight), damage, herb,carn);
    }
    public static int purchaseCost(Blueprint old, Blueprint next, Map<Part,PartSpec> specs) {
        // Full salvage value prevents charging again for an existing body.
        return next.stats(specs).cost - old.stats(specs).cost;
    }
}
