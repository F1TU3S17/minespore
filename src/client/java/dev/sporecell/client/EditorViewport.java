package dev.sporecell.client;

import dev.sporecell.core.Blueprint;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

/** One orthographic transform for rendering, projection and surface picking. */
final class EditorViewport {
    float yaw=25,pitch=52,zoom=44,centerX,centerY;
    Matrix4f matrix() {return new Matrix4f().translation(centerX,centerY,150).scale(zoom,-zoom,zoom)
        .rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));}
    Vector3f project(double x,double y,double z) {return matrix().transformPosition(new Vector3f((float)x,(float)y,(float)z));}
    record Hit(int index,Vector3f point,Vector3f normal,float distance) {}
    Vector3f[] ray(double mx,double my) {
        Matrix4f inverse=matrix().invert();
        Vector3f origin=inverse.transformPosition(new Vector3f((float)mx,(float)my,10000));
        Vector3f end=inverse.transformPosition(new Vector3f((float)mx,(float)my,-10000));
        return new Vector3f[]{origin,end.sub(origin).normalize()};
    }
    Vector3f ground(double mx,double my) {
        var r=ray(mx,my);if(Math.abs(r[1].y)<.001)return null;
        return new Vector3f(r[1]).mul(-r[0].y/r[1].y).add(r[0]);
    }
    Hit pick(List<Blueprint.Piece> pieces,double mx,double my,boolean bodyOnly) {
        var ray=ray(mx,my);Hit best=null;
        for(int i=0;i<pieces.size();i++) {
            var p=pieces.get(i);if(bodyOnly&&p.mounted())continue;
            float radius=(float)(p.mounted()?.28*p.size():.295);
            float y=(float)(p.mounted()?p.py()+.48*p.size():.21),h=(float)(p.mounted()?.29*p.size():.21);
            float[] min={(float)p.px()-radius,y-h,(float)p.pz()-radius};
            float[] max={(float)p.px()+radius,y+h,(float)p.pz()+radius};
            float near=0,far=Float.MAX_VALUE;Vector3f normal=new Vector3f();boolean valid=true;
            for(int axis=0;axis<3;axis++) {
                float origin=ray[0].get(axis),direction=ray[1].get(axis);
                if(Math.abs(direction)<.00001) {if(origin<min[axis]||origin>max[axis])valid=false;continue;}
                float t0=(min[axis]-origin)/direction,t1=(max[axis]-origin)/direction;
                float entry=Math.min(t0,t1),exit=Math.max(t0,t1);
                if(entry>near){near=entry;normal.zero().setComponent(axis,direction>0?-1:1);}
                far=Math.min(far,exit);if(near>far)valid=false;
            }
            if(valid&&(best==null||near<best.distance()))best=new Hit(i,new Vector3f(ray[1]).mul(near).add(ray[0]),normal,near);
        }
        return best;
    }
}
