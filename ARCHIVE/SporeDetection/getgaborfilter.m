function gb = getgaborfilter(lambda, theta)
sigma = 1; gamma = 0.5; psi = 0; theta =  pi-(pi/2 + theta);
sigma_x = sigma;
sigma_y = sigma/gamma;

nstds = 3;
xmax = max(abs(nstds*sigma_x*cos(theta)),abs(nstds*sigma_y*sin(theta)));
xmax = ceil(max(1,xmax));
ymax = max(abs(nstds*sigma_x*sin(theta)),abs(nstds*sigma_y*cos(theta)));
ymax = ceil(max(1,ymax));
xmin = -xmax; ymin = -ymax;
[x,y] = meshgrid(xmin:xmax,ymin:ymax);

x_theta=x*cos(theta)+y*sin(theta);
y_theta=-x*sin(theta)+y*cos(theta);

gb= exp(-.5*(x_theta.^2/sigma_x^2+y_theta.^2/sigma_y^2)).*cos(2*pi/lambda*x_theta+psi);
gb(abs(gb)<0.005)=0;
gb(gb>0) = gb(gb>0)/sum(gb(gb>0));
gb(gb<0) = -gb(gb<0)/sum(gb(gb<0));
gb = -gb;
end