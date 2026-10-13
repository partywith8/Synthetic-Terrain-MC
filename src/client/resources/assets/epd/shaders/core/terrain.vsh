#version 330
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };
layout(std140) uniform Projection { mat4 ProjMat; };
layout(std140) uniform MotionParams { vec4 Control; vec4 Region[8]; vec4 Timing[8]; };
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 Column;
out vec2 texCoord;
out vec4 vertexColor;

float ease(float x) { x=clamp(x,0.0,1.0); return x*x*(3.0-2.0*x); }

// Keep this function numerically aligned with MotionEvent.offset. Vertices share
// one integer column owner, so complete blocks translate without becoming wedges.
float movement(int i) {
    vec4 r=Region[i], t=Timing[i];
    float elapsed=t.y;
    bool hold=t.w>=4.0;
    int axis=int(t.w)%4, kind=int(t.x);
    if(elapsed<=0.0 || (!hold && elapsed>=t.z)) return 0.0;
    vec2 p=vec2(Column)+.5-r.xy;
    if((axis%2)!=0) p=p.yx;
    if(axis>=2) p.x=-p.x;
    // Stress has a fixed world phase and no footprint boundary. Newly loaded
    // chunks join the same field without recentering the surface under riders.
    float distance=kind==8?0.0:length(p);
    float d=distance/r.z;
    if(d>=1.0) return 0.0;
    float edge=kind==8?1.0:1.0-ease((d-.70)/.30);
    float envelope=ease(elapsed/120.0)*(hold?1.0:ease((t.z-elapsed)/120.0));
    float phase=clamp(elapsed/t.z,0.0,1.0), shape=0.0;
    if(kind==0) shape=exp(-pow((p.x/r.z+1.25-phase*2.5)/.22,2.0));
    else if(kind==1) shape=cos(length(p)*.22-elapsed*.045)*.5;
    else if(kind==2) shape=exp(-pow((p.x/r.z+1.3-phase*2.6)/.065,2.0));
    else if(kind==3) shape=pow(max(0.0,1.0-abs(p.x/r.z+.18*sin(p.y/r.z*5.0))-abs(p.y/r.z)*.85),1.3);
    else if(kind==4) shape=exp(-pow((p.y/r.z-.15*sin(p.x/r.z*4.0))/.18,2.0));
    else if(kind==5) shape=-exp(-pow((p.y/r.z-.12*sin(p.x/r.z*5.0))/.16,2.0));
    else if(kind==6) shape=.5+.5*sin(floor(float(Column.x)/8.0)*1.7+floor(float(Column.y)/8.0)*2.3+(hold?1.8:elapsed*.012));
    else if(kind==7) shape=floor(max(0.0,1.0-d)*4.0)/4.0;
    else if(kind==8) shape=.55*sin(p.x*.028-elapsed*.014)
            +.30*sin(p.y*.035+elapsed*.011)+.15*sin((p.x+p.y)*.018-elapsed*.009);
    return r.w*edge*envelope*shape;
}
void main() {
    float shift=0.0;
    for(int i=0;i<int(Control.y);i++) shift+=movement(i);
    shift=clamp(shift,-40.0,64.0)*Control.x;
    gl_Position=ProjMat*ModelViewMat*vec4(Position+vec3(0,shift,0),1);
    texCoord=UV0;
    vertexColor=Color;
}
