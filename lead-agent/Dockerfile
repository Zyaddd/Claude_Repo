FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY . .
ENV NODE_ENV=production PORT=8080 DATA_DIR=/data
VOLUME /data
EXPOSE 8080
# Runs as root on purpose: hosting volumes mounted at /data are usually root-owned,
# and a non-root user couldn't save leads there.
CMD ["node", "server.js"]
