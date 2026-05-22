<?php

namespace App\Model;

class User
{
    private int $id;
    private string $email;
    public string $displayName = '';

    public function __construct(int $id, string $email)
    {
        $this->id = $id;
        $this->email = $email;
    }

    public function getId(): int
    {
        return $this->id;
    }

    public function getEmail(): string
    {
        return $this->email;
    }

    protected function hashEmail(): string
    {
        return md5($this->email);
    }
}
