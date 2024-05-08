package main

import (
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"time"
)

type Head struct {
	Type int8
	Size int32
}

func main() {
	// 连接 localhost:1234 tcp
	conn, err := net.Dial("tcp", "localhost:1234")
	if err != nil {
		fmt.Println(err)
		return
	}
	// go io.Copy(conn, os.Stdin)
	go func() {
		for i := 0; i < 5; i++ {
			conn.Write([]byte("png\n"))
		}
		time.Sleep(3 * time.Second)
		conn.Close()
	}()
	count := 0
	_ = time.NewTicker(time.Second)
	go func() {
		for i := 0; i < 10; i++ {
			conn.Write([]byte("png\n"))
		}

	}()
	for {
		head := Head{}
		binary.Read(conn, binary.LittleEndian, &head)
		// fmt.Println("接收到消息：", head.Type, head.Size)
		buf := make([]byte, head.Size)
		_, err := io.ReadFull(conn, buf)
		if err != nil {
			fmt.Println(err)
			return
		}
		// fmt.Println("Readed", n, "bytes")
		count++
		// switch head.Type {
		// case 1:
		// 	os.WriteFile("a.png", buf, 0666)
		// case 2:
		// 	os.WriteFile("b.jpg", buf, 0666)
		// }
	}

}
